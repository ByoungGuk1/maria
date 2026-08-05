package com.app.maria.domain.inbound.service;

import com.app.maria.domain.inbound.dto.request.InboundRequestDTO;
import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;

public interface InboundService {
    InboundResponseDTO processInbound(
            InboundRequestDTO request);
}
