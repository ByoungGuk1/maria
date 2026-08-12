package com.app.maria.domain.accountclosure.api;

import com.app.maria.domain.accountclosure.dto.request.AccountClosureApplyRequestDTO;
import com.app.maria.domain.accountclosure.service.AccountClosureService;
import com.app.maria.global.response.ApiResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/account-closures")
@PreAuthorize("hasAnyRole('ADMIN', 'SETTLEMENT', 'REVIEWER', 'VIEWER')")
public class AccountClosureApi {
    private final AccountClosureService accountClosureService;

    @PostMapping
    public ResponseEntity<ApiResponseDTO<Long>> applyClosure(
            @Valid @RequestBody AccountClosureApplyRequestDTO requestDTO) {
        Long closureRequestId =
                accountClosureService.applyClosure(requestDTO.getCustomerId(), requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseDTO.of("계좌 해지 신청 완료", closureRequestId));
    }
}
