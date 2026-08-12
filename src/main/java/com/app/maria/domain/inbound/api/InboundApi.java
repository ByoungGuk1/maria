package com.app.maria.domain.inbound.api;

import com.app.maria.domain.inbound.dto.request.InboundRequestDTO;
import com.app.maria.domain.inbound.dto.response.AccountHoldingResponseDTO;
import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.service.InboundService;
import com.app.maria.global.response.ApiResponseDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/inbounds")
public class InboundApi {

    private final InboundService inboundService;

    @PostMapping
    public ResponseEntity<ApiResponseDTO<InboundResponseDTO>> processInbound(
            @Valid @RequestBody InboundRequestDTO request) {
        InboundResponseDTO result = inboundService.processInbound(request);
        return ResponseEntity.ok(ApiResponseDTO.of("입고대상 수량 계산 성공", result));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SETTLEMENT', 'REVIEWER', 'VIEWER')")
    @GetMapping("/holdings")
    public ResponseEntity<ApiResponseDTO<List<AccountHoldingResponseDTO>>> getHoldings(
            @RequestParam @Positive Long accountId) {
        List<AccountHoldingResponseDTO> result = inboundService.getHoldings(accountId);
        return ResponseEntity.ok(ApiResponseDTO.of("계좌 보유종목 조회 성공", result));
    }

}
