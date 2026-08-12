package com.app.maria.domain.accountclosure.service;

import com.app.maria.domain.accountclosure.dto.request.AccountClosureApplyRequestDTO;

public interface AccountClosureService {
    Long applyClosure(Long customerId, AccountClosureApplyRequestDTO requestDTO);

    void rejectClosure(Long adminId, Long closureRequestId, String reason);
}
