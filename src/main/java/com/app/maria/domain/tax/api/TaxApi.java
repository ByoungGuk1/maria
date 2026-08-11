package com.app.maria.domain.tax.api;

import com.app.maria.domain.tax.dto.response.TaxCalculationResponseDTO;
import com.app.maria.domain.tax.service.TaxCalculationService;
import com.app.maria.global.response.ApiResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
public class TaxApi {
    private final TaxCalculationService taxCalculationService;

    @GetMapping("/preview/{accountId}")
    public ResponseEntity<ApiResponseDTO<TaxCalculationResponseDTO>> preview(
            @PathVariable Long accountId) {
        return ResponseEntity.ok(
                ApiResponseDTO.of("세금계산 성공", taxCalculationService.taxCalculate(accountId)));
    }
}
