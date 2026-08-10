package com.app.maria.domain.account.service;

import com.app.maria.domain.account.dto.AccountDTO;
import com.app.maria.domain.account.dto.request.AccountReapplyRequestDTO;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface AccountTransactionalService {
  AccountDTO updateLimit(Long customerId, BigDecimal expectedCurrentLimit, BigDecimal newLimit, LocalDateTime changedAt);
  AccountDTO apply(AccountDTO account, LocalDateTime appliedAt, boolean autoApprove);
  AccountDTO approve(Long accountId, BigDecimal expectedLimit, LocalDateTime openedAt);
  AccountDTO reject(Long accountId, String reason, LocalDateTime changedAt);
  AccountDTO reapply(Long accountId, AccountReapplyRequestDTO request, LocalDateTime appliedAt);
  AccountDTO override(Long accountId, String reason, LocalDateTime openedAt);
}
