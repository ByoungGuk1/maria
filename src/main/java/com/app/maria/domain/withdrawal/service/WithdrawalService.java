package com.app.maria.domain.withdrawal.service;

import com.app.maria.domain.withdrawal.dto.WithdrawalAllocationDTO;
import com.app.maria.domain.withdrawal.dto.request.WithdrawalRequestDTO;
import java.util.List;

public interface WithdrawalService {

    List<WithdrawalAllocationDTO> withdraw(WithdrawalRequestDTO requestDTO);
}
