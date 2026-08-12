package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.AccountLimitUsageDTO;
import com.app.maria.domain.account.dto.request.AccountLimitUpdateRequestDTO;
import com.app.maria.domain.account.dto.request.AccountReapplyRequestDTO;
import com.app.maria.domain.account.dto.request.AccountRequestDTO;
import com.app.maria.domain.account.dto.request.AccountSearchRequestDTO;
import com.app.maria.domain.account.dto.response.AccountLogResponseDTO;
import com.app.maria.domain.account.dto.response.AccountResponseDTO;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.exception.InvalidAccountRequestException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.provider.MydataProvider;
import com.app.maria.domain.account.type.Status;
import com.app.maria.global.clock.service.BusinessClockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {
    private static final BigDecimal MAX_LIMIT_AMOUNT = BigDecimal.valueOf(50_000_000L);
    private static final BigDecimal MIN_LIMIT_AMOUNT = BigDecimal.ONE;
    private static final LocalDate RIA_APPLICATION_START_DATE = LocalDate.of(2026, 3, 23);
    private static final LocalDate RIA_APPLICATION_END_DATE = LocalDate.of(2026, 12, 31);

    private final AccountMapper accountMapper;
    private final MydataProvider mydataProvider;
    private final AccountLogService accountLogService;
    private final BusinessClockService businessClockService;
    private final AccountTransactionalService accountTransactionalService;
    private final AccountMydataSyncService accountMydataSyncService;

    @Override
    public List<AccountResponseDTO> findAll() {
        return accountMapper.selectAllAccount().stream().map(AccountResponseDTO::new).toList();
    }

    @Override
    public BigDecimal getAvailableLimit(Long customerId) {
        validateCustomerExists(customerId);
        return calculateAvailableLimit(customerId);
    }

    @Override
    public AccountResponseDTO updateAccountLimit(AccountLimitUpdateRequestDTO requestDTO) {
        validateCustomerExists(requestDTO.getCustomerId());
        Long customerId = requestDTO.getCustomerId();
        BigDecimal newLimitAmount = requestDTO.getLimitAmount();

        validateLimitInput(newLimitAmount);
        validateLimitAvailability(newLimitAmount, calculateAvailableLimit(customerId));
        AccountDTO updatedAccount =
                accountTransactionalService.updateLimit(
                        customerId,
                        requestDTO.getExpectedCurrentLimit(),
                        newLimitAmount,
                        businessClockService.now());
        if (updatedAccount.getStatus() == Status.OPENED) {
            accountMydataSyncService.updateLimit(updatedAccount);
        }
        return new AccountResponseDTO(updatedAccount);
    }

    @Override
    public AccountResponseDTO applyAccount(AccountRequestDTO requestDTO) {
        Long customerId = requestDTO.getCustomerId();
        validateCustomerExists(customerId);
        LocalDateTime appliedAt = businessClockService.now();

        AccountDTO account = requestDTO.toAccountDTO();
        validateLimitInput(account.getLimitAmount());
        BigDecimal availableLimit = calculateAvailableLimit(customerId);
        boolean autoApprove =
                isWithinApplicationPeriod(appliedAt)
                        && account.getLimitAmount().compareTo(availableLimit) <= 0
                        && availableLimit.compareTo(MIN_LIMIT_AMOUNT) >= 0;
        AccountDTO appliedAccount =
                accountTransactionalService.apply(account, appliedAt, autoApprove);
        if (appliedAccount.getStatus() == Status.OPENED) {
            accountMydataSyncService.create(appliedAccount);
        }
        return new AccountResponseDTO(appliedAccount);
    }

    @Override
    public AccountResponseDTO approveAccount(Long accountId) {
        AccountDTO account =
                accountMapper
                        .selectByAccountId(accountId)
                        .orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
        LocalDateTime openedAt = businessClockService.now();
        validateLimitAvailability(
                account.getLimitAmount(), calculateAvailableLimit(account.getCustomerId()));
        AccountDTO openedAccount =
                accountTransactionalService.approve(accountId, account.getLimitAmount(), openedAt);
        accountMydataSyncService.create(openedAccount);
        return new AccountResponseDTO(openedAccount);
    }

    @Override
    public AccountResponseDTO rejectAccount(Long accountId, String reason) {
        AccountDTO rejectedAccount =
                accountTransactionalService.reject(
                        accountId, normalizeReason(reason), businessClockService.now());
        return new AccountResponseDTO(rejectedAccount);
    }

    @Override
    public AccountResponseDTO reapplyAccountByAccountId(
            Long accountId, AccountReapplyRequestDTO requestDTO) {
        AccountDTO foundAccount =
                accountMapper
                        .selectByAccountId(accountId)
                        .orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
        LocalDateTime appliedAt = getApplicationTime();
        BigDecimal limitAmount =
                requestDTO.getLimitAmount() == null
                        ? foundAccount.getLimitAmount()
                        : requestDTO.getLimitAmount();
        validateLimitInput(limitAmount);
        validateLimitAvailability(
                limitAmount, calculateAvailableLimit(foundAccount.getCustomerId()));
        AccountDTO reappliedAccount =
                accountTransactionalService.reapply(accountId, requestDTO, appliedAt);
        return new AccountResponseDTO(reappliedAccount);
    }

    @Override
    public AccountResponseDTO getAccountByAccountId(Long accountId) {
        AccountDTO account =
                accountMapper
                        .selectByAccountId(accountId)
                        .orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
        return new AccountResponseDTO(account);
    }

    @Override
    public List<AccountLogResponseDTO> getStatusLogsByAccountId(Long accountId) {
        accountMapper
                .selectByAccountId(accountId)
                .orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
        return accountLogService.getStatusLogs(accountId);
    }

    @Override
    public AccountResponseDTO overrideAccount(Long accountId, String reason) {
        String normalizedReason = normalizeReason(reason);
        AccountDTO account =
                accountMapper
                        .selectByAccountId(accountId)
                        .orElseThrow(() -> new AccountNotFoundException("계좌 조회 실패"));
        LocalDateTime openedAt = businessClockService.now();
        validateLimitAvailability(
                account.getLimitAmount(), calculateAvailableLimit(account.getCustomerId()));
        AccountDTO openedAccount =
                accountTransactionalService.override(accountId, normalizedReason, openedAt);
        accountMydataSyncService.create(openedAccount);
        return new AccountResponseDTO(openedAccount);
    }

    private BigDecimal calculateAvailableLimit(Long customerId) {
        String ciHash =
                accountMapper
                        .selectCiHashByCustomerId(customerId)
                        .orElseThrow(() -> new AccountNotFoundException("개설할 계좌의 사용자를 찾을 수 없습니다."));
        return MAX_LIMIT_AMOUNT
                .subtract(mydataProvider.getExternalConfiguredLimit(ciHash))
                .max(BigDecimal.ZERO);
    }

    private void validateLimitAvailability(BigDecimal requestedLimit, BigDecimal availableLimit) {
        if (availableLimit.compareTo(MIN_LIMIT_AMOUNT) < 0) {
            throw new InvalidAccountRequestException("설정 가능한 RIA 납입한도가 없어 계좌를 개설할 수 없습니다.");
        }
        if (requestedLimit.compareTo(availableLimit) > 0) {
            throw new InvalidAccountRequestException(
                    "계좌의 한도는 " + MIN_LIMIT_AMOUNT + "부터 " + availableLimit + "이하 입니다.");
        }
    }

    private void validateLimitInput(BigDecimal requestedLimit) {
        if (requestedLimit == null) {
            throw new InvalidAccountRequestException("계좌 한도 입력이 필요합니다.");
        }
        if (requestedLimit.compareTo(MIN_LIMIT_AMOUNT) < 0) {
            throw new InvalidAccountRequestException(
                    "계좌의 한도는 " + MIN_LIMIT_AMOUNT + "원 이상이어야 합니다.");
        }
        if (requestedLimit.stripTrailingZeros().scale() > 0) {
            throw new InvalidAccountRequestException("계좌의 한도는 원 단위로 입력해야 합니다.");
        }
        if (requestedLimit.compareTo(MAX_LIMIT_AMOUNT) > 0) {
            throw new InvalidAccountRequestException("계좌의 한도는 " + MAX_LIMIT_AMOUNT + "원 이하여야 합니다.");
        }
    }

    private void validateCustomerExists(Long customerId) {
        if (!accountMapper.existsCustomerById(customerId)) {
            throw new AccountNotFoundException("개설할 계좌의 사용자를 찾을 수 없습니다.");
        }
    }

    private LocalDateTime getApplicationTime() {
        LocalDateTime applicationTime = businessClockService.now();
        if (!isWithinApplicationPeriod(applicationTime)) {
            throw new InvalidAccountRequestException("RIA 계좌 신청 가능 기간이 아닙니다.");
        }
        return applicationTime;
    }

    private boolean isWithinApplicationPeriod(LocalDateTime applicationTime) {
        LocalDate today = applicationTime.toLocalDate();
        return !today.isBefore(RIA_APPLICATION_START_DATE)
                && !today.isAfter(RIA_APPLICATION_END_DATE);
    }

    private String normalizeReason(String reason) {
        return reason.trim();
    }

    @Override
    @Transactional(readOnly = true)
    public int getAppliedAccountCount() {
        return accountMapper.countByStatus(Status.APPLIED);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountLimitUsageDTO> selectAccountLimitUsage() {
        return accountMapper.selectAccountLimitUsage();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountLimitUsageDTO> getAppliedAccounts() {
        return accountMapper.selectAppliedAccounts();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountLimitUsageDTO> searchAccounts(AccountSearchRequestDTO request) {
        return accountMapper.searchAccounts(request.toAccountSearchDTO());
    }
}
