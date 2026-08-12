package com.app.maria.domain.tax.dto.response;

import com.app.maria.domain.tax.dto.TaxCalculationResultDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaxCalculationPreviewResponseDTO {
    private Long accountId;
    private TaxCalculationResultDTO taxCalculationResultDTO;

    public static TaxCalculationPreviewResponseDTO of(
            Long accountId, TaxCalculationResultDTO taxCalculationResultDTO) {
        return TaxCalculationPreviewResponseDTO.builder()
                .accountId(accountId)
                .taxCalculationResultDTO(taxCalculationResultDTO)
                .build();
    }
}
