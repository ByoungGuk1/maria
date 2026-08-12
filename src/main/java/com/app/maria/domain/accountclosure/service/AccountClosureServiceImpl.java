package com.app.maria.domain.accountclosure.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.exception.AccountNotFoundException;
import com.app.maria.domain.account.mapper.AccountMapper;
import com.app.maria.domain.account.type.Status;
import com.app.maria.domain.accountclosure.dto.AccountClosureDTO;
import com.app.maria.domain.accountclosure.dto.request.AccountClosureApplyRequestDTO;
import com.app.maria.domain.accountclosure.exception.AccountClosureNotAllowedException;
import com.app.maria.domain.accountclosure.exception.AccountClosureNotFoundException;
import com.app.maria.domain.accountclosure.exception.AccountClosureProcessingException;
import com.app.maria.domain.accountclosure.mapper.AccountClosureMapper;
import com.app.maria.domain.accountclosure.type.AccountClosureStatus;
import com.app.maria.global.client.generalaccount.GeneralAccountClient;
import com.app.maria.global.client.generalaccount.dto.request.GeneralAccountRequestDTO;
import com.app.maria.global.client.generalaccount.dto.response.GeneralAccountResponseDTO;
import com.app.maria.global.client.generalaccount.type.GeneralAccountStatus;
import com.app.maria.global.clock.service.BusinessClockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class AccountClosureServiceImpl implements AccountClosureService {
    private final AccountMapper accountMapper;
    private final AccountClosureMapper accountClosureMapper;
    private final BusinessClockService businessClockService;
    private final GeneralAccountClient generalAccountClient;

    @Override
    public Long applyClosure(Long customerId, AccountClosureApplyRequestDTO requestDTO) {
        AccountDTO account =
                accountMapper
                        .selectByCustomerId(customerId)
                        .orElseThrow(() -> new AccountNotFoundException("해지할 계좌가 존재하지 않습니다."));
        if (account.getStatus() != Status.OPENED) {
            throw new AccountClosureNotAllowedException("개설 완료된 계좌만 해지를 할 수 있습니다.");
        }
        String ciHash =
                accountMapper
                        .selectCiHashByCustomerId(account.getCustomerId())
                        .orElseThrow(() -> new AccountNotFoundException("존재하지않는 고객입니다."));

        GeneralAccountRequestDTO generalAccountRequest =
                GeneralAccountRequestDTO.builder()
                        .ciHash(ciHash)
                        .generalAccountId(requestDTO.getDestinationGeneralAccountId())
                        .build();
        GeneralAccountResponseDTO response =
                generalAccountClient.verifyGeneralAccount(generalAccountRequest);

        if (response.getStatus() != GeneralAccountStatus.ACTIVE) {
            throw new AccountClosureNotAllowedException("활성 상태의 일반계좌만 해지 정산 계좌로 선택할 수 있습니다.");
        }
        int updatedAccountRows = accountMapper.requestClosure(account.getAccountId());
        if (updatedAccountRows != 1) {
            throw new AccountClosureNotAllowedException("계좌 상태가 변경되어 해지를 신청할 수 없습니다.");
        }
        AccountClosureDTO closure =
                AccountClosureDTO.builder()
                        .accountId(account.getAccountId())
                        .destinationGeneralAccountId(response.getGeneralAccountId())
                        .earlyWithdrawalAgreed(requestDTO.isEarlyWithdrawalAgreed())
                        .status(AccountClosureStatus.REQUESTED)
                        .requestedAt(businessClockService.now())
                        .build();
        int insertedClosureRows = accountClosureMapper.insertClosureRequest(closure);
        if (insertedClosureRows != 1) {
            throw new AccountClosureProcessingException("계좌 해지 신청 저장에 실패했습니다.");
        }
        return closure.getClosureRequestId();
    }

    @Override
    public void rejectClosure(Long adminId, Long closureRequestId, String reason) {
        // 신청 건 잠금 조회
        AccountClosureDTO closure =
                accountClosureMapper
                        .selectByIdForUpdate(closureRequestId)
                        .orElseThrow(
                                () -> new AccountClosureNotFoundException("계좌 해지 신청을 찾을 수 없습니다."));
        // REQUSTED상태 검증
        if (closure.getStatus() != AccountClosureStatus.REQUESTED) {
            throw new AccountClosureNotAllowedException("이미 처리된 계좌 해지 신청입니다.");
        }
        closure.setProcessedAt(businessClockService.now());
        closure.setProcessedBy(adminId);
        closure.setRejectionReason(reason);

        int rejectedClosureRows = accountClosureMapper.rejectClosureRequest(closure);
        if (rejectedClosureRows != 1) {
            throw new AccountClosureProcessingException("계좌 해지 신청 반려 처리에 실패했습니다.");
        }
        int reopenedRows = accountMapper.reopenAfterClosureRejection(closure.getAccountId());
        if (reopenedRows != 1) {
            throw new AccountClosureProcessingException("해지 반려 후 계좌 상태 복구에 실패했습니다.");
        }
    }
}
