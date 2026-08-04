package com.app.maria.domain.inbound.api;

import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.service.InboundService;
import com.app.maria.global.response.ApiResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inbounds")
public class InboundApi {

    private final InboundService inboundService;

    @PostMapping
    public ResponseEntity<ApiResponseDTO<InboundResponseDTO>> processInbound(
            @RequestParam Long accountId,
            @RequestParam Long foreignProductId,
            @RequestParam BigDecimal requestedQty,
            @RequestParam BigDecimal currentHoldingAtRequest) {
        InboundResponseDTO result = inboundService.processInbound(
                accountId,
                foreignProductId,
                requestedQty,
                currentHoldingAtRequest);
        return ResponseEntity.ok(ApiResponseDTO.of("입고대상 수량 계산 성공", result));
    }
}
