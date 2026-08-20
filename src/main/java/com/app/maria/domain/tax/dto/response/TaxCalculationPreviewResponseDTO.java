package com.app.maria.domain.tax.dto.response;

import com.app.maria.domain.tax.dto.TaxCalculationDTO;
import com.app.maria.domain.tax.dto.TaxCalculationResultDTO;
import java.time.LocalDateTime;
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
    // 세제혜택이 일부감액/배제 상태라면 그 사유 (account_benefit_log 최신 이력)
    private String benefitChangeReason;
    private LocalDateTime benefitChangedAt;
    // 이미 확정신고/조기인출추징으로 저장된 계산이 있는지 - 있으면 관리자가 또 만질 필요 없음을 알려준다
    private TaxCalculationDTO latestSavedCalculation;

    public static TaxCalculationPreviewResponseDTO of(
            Long accountId,
            TaxCalculationResultDTO taxCalculationResultDTO,
            String benefitChangeReason,
            LocalDateTime benefitChangedAt,
            TaxCalculationDTO latestSavedCalculation) {
        return TaxCalculationPreviewResponseDTO.builder()
                .accountId(accountId)
                .taxCalculationResultDTO(taxCalculationResultDTO)
                .benefitChangeReason(benefitChangeReason)
                .benefitChangedAt(benefitChangedAt)
                .latestSavedCalculation(latestSavedCalculation)
                .build();
    }
}
