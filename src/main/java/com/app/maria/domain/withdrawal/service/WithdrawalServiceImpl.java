package com.app.maria.domain.withdrawal.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.type.Status;
import com.app.maria.domain.withdrawal.dto.LeftAmountDTO;
import com.app.maria.domain.withdrawal.dto.WithdrawalAllocationDTO;
import com.app.maria.domain.withdrawal.dto.request.WithdrawalRequestDTO;
import com.app.maria.domain.withdrawal.exception.InsufficientWithdrawalAmountException;
import com.app.maria.domain.withdrawal.exception.WithdrawalNotAllowedException;
import com.app.maria.domain.withdrawal.mapper.WithdrawalMapper;
import com.app.maria.domain.withdrawal.type.WithdrawalType;
import com.app.maria.global.clock.service.BusinessClockService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WithdrawalServiceImpl implements WithdrawalService {

    private final BusinessClockService businessClockService;
    private final AccountMapper accountMapper;
    private final WithdrawalMapper withdrawalMapper;

    @Override
    @Transactional
    public List<WithdrawalAllocationDTO> withdraw(WithdrawalRequestDTO requestDTO) {
        Long accountId = requestDTO.getAccountId();
        BigDecimal requestedAmount = requestDTO.getRequestedAmount();

        AccountDTO account =
                accountMapper
                        .selectByAccountIdForUpdate(accountId)
                        .orElseThrow(() -> new AccountNotFoundException("인출 대상 계좌가 존재하지 않습니다."));

        if (account.getStatus() != Status.OPENED) {
            throw new WithdrawalNotAllowedException("개설 완료된 계좌만 인출할 수 있습니다.");
        }

        if (requestedAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new WithdrawalNotAllowedException("인출 요청금액은 0보다 커야 합니다.");
        }

        if (account.getAmount().compareTo(requestedAmount) < 0) {
            throw new InsufficientWithdrawalAmountException("계좌 잔액보다 많은 금액을 인출할 수 없습니다.");
        }

        List<LeftAmountDTO> leftAmounts =
                withdrawalMapper.selectAvailableLeftAmountsByAccountId(accountId);
        LocalDateTime currentDatetime = businessClockService.now();

        BigDecimal totalPrincipal =
                leftAmounts.stream()
                        .map(LeftAmountDTO::getCurAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal earnings = account.getAmount().subtract(totalPrincipal).max(BigDecimal.ZERO);
        BigDecimal earningsAllocation = requestedAmount.min(earnings);
        BigDecimal remainingRequest = requestedAmount.subtract(earningsAllocation);

        List<LeftAmountDTO> maturedLeftAmounts =
                leftAmounts.stream()
                        .filter(
                                leftAmount ->
                                        !leftAmount
                                                .getFinalAt()
                                                .plusYears(1)
                                                .isAfter(currentDatetime))
                        .toList();

        List<WithdrawalAllocationDTO> allocations = new ArrayList<>();
        if (earningsAllocation.compareTo(BigDecimal.ZERO) > 0) {
            allocations.add(
                    WithdrawalAllocationDTO.builder()
                            .leftAmountId(null)
                            .allocatedAmount(earningsAllocation)
                            .withdrawalAt(currentDatetime)
                            .type(WithdrawalType.EARNINGS_ONLY)
                            .build());
        }
        allocations.addAll(
                allocateMaturedPrincipalFifo(
                        remainingRequest, maturedLeftAmounts, currentDatetime));

        return allocations;
    }

    private List<WithdrawalAllocationDTO> allocateMaturedPrincipalFifo(
            BigDecimal amountToAllocate,
            List<LeftAmountDTO> maturedLeftAmounts,
            LocalDateTime currentDatetime) {
        List<WithdrawalAllocationDTO> allocations = new ArrayList<>();
        BigDecimal remainingAmount = amountToAllocate;

        for (LeftAmountDTO leftAmount : maturedLeftAmounts) {
            if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }

            BigDecimal allocatedAmount = remainingAmount.min(leftAmount.getCurAmount());

            allocations.add(
                    WithdrawalAllocationDTO.builder()
                            .leftAmountId(leftAmount.getLeftAmountId())
                            .allocatedAmount(allocatedAmount)
                            .withdrawalAt(currentDatetime)
                            .type(WithdrawalType.MATURED_PRINCIPAL_INCLUDED)
                            .build());

            remainingAmount = remainingAmount.subtract(allocatedAmount);
        }

        return allocations;
    }
}
