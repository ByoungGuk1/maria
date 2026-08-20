package com.app.maria.domain.withdrawal.service;

import com.app.maria.domain.withdrawal.dto.WithdrawalResultDTO;
import com.app.maria.domain.withdrawal.dto.request.WithdrawalRequestDTO;
import java.math.BigDecimal;

public interface WithdrawalService {

    WithdrawalResultDTO withdraw(WithdrawalRequestDTO requestDTO);

    WithdrawalResultDTO withdrawForClosure(WithdrawalRequestDTO requestDTO);

    boolean hasImmaturePrincipal(Long accountId);

    BigDecimal getImmaturePrincipalAmount(Long accountId);

    BigDecimal getImmatureAllocatedAmount(Long withdrawalId);
}
