package com.app.maria.domain.inbound.service;

import com.app.maria.domain.inbound.dto.request.InboundRequestDTO;
import com.app.maria.domain.inbound.dto.response.AccountHoldingResponseDTO;
import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import java.util.List;

public interface InboundService {
    InboundResponseDTO processInbound(InboundRequestDTO request);

    List<AccountHoldingResponseDTO> getHoldings(Long accountId);
}
