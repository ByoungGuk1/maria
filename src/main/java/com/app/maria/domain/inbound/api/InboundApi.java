package com.app.maria.domain.inbound.api;

import com.app.maria.domain.inbound.dto.request.InboundRequestDTO;
import com.app.maria.domain.inbound.dto.response.InboundResponseDTO;
import com.app.maria.domain.inbound.service.InboundService;
import com.app.maria.global.response.ApiResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/inbounds")
public class InboundApi {

    private final InboundService inboundService;

    @PostMapping
    public ResponseEntity<ApiResponseDTO<InboundResponseDTO>> processInbound(
            @Valid @RequestBody InboundRequestDTO request) {
        InboundResponseDTO result = inboundService.processInbound(request);
        return ResponseEntity.ok(ApiResponseDTO.of("입고대상 수량 계산 성공", result));
    }
}
