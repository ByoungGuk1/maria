package com.app.maria.domain.tax.dto;

import com.app.maria.domain.tax.type.TaxBasisType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaxCalculationDTO {
    private Long calcId;
    private Long accountId;
    private LocalDateTime calculatedAt;
    private TaxBasisType basisType;
    private BigDecimal sellAmount;
    private BigDecimal gainAmount;
    private BigDecimal gainWeighted;
    private BigDecimal extAmount;
    private BigDecimal ratio;
    private BigDecimal deduction;
    private BigDecimal tax;

    public static TaxCalculationDTO of(
            Long accountId,
            TaxBasisType basisType,
            LocalDateTime calculatedAt,
            TaxCalculationResultDTO result) {
        return TaxCalculationDTO.builder()
                .accountId(accountId)
                .basisType(basisType)
                .calculatedAt(calculatedAt)
                .sellAmount(result.getWeightedSell())
                .gainAmount(result.getOriginalGainAmount())
                .gainWeighted(result.getWeightedGain())
                .extAmount(result.getWeightedExternalAmount())
                .ratio(result.getAdjustRatio())
                .deduction(result.getFinalDeduction())
                .tax(result.getFinalTax())
                .build();
    }
}
