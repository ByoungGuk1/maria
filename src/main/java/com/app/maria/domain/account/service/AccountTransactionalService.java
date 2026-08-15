package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.request.AccountReapplyRequestDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface AccountTransactionalService {
    AccountDTO updateLimit(
            Long adminId,
            Long customerId,
            BigDecimal expectedCurrentLimit,
            BigDecimal newLimit,
            LocalDateTime changedAt);

    AccountDTO apply(
            Long adminId, AccountDTO account, LocalDateTime appliedAt, boolean autoApprove);

    AccountDTO approve(
            Long adminId, Long accountId, BigDecimal expectedLimit, LocalDateTime openedAt);

    AccountDTO reject(Long adminId, Long accountId, String reason, LocalDateTime changedAt);

    AccountDTO reapply(
            Long adminId,
            Long accountId,
            AccountReapplyRequestDTO request,
            LocalDateTime appliedAt);

    AccountDTO override(Long adminId, Long accountId, String reason, LocalDateTime openedAt);

    /**
     * account.amount를 직접 변경하는 유일한 정당 진입점. RIA 내 현금 직접입금은 금지되어 있음.(D2), 이 메서드는 매도대금 가환전에서만 호출됨. 새로운
     * 호출자를 추가하기 전에 AccountAmountWritePathTest를 반드시 확인할 것.
     */
    void updateAmount(AccountDTO newAmountAccount);
}
