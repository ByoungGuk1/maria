package com.app.maria.domain.inbound.service;

import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import java.math.BigDecimal;

public interface InboundService {
    InboundResponseDTO processInbound(
            Long accountId,
            Long foreignProductId,
            BigDecimal requestedQty,
            BigDecimal currentHoldingAtRequest);
}
