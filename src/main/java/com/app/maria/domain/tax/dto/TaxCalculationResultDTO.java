package com.app.maria.domain.tax.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TaxCalculationResultDTO {
    private BigDecimal originalGainAmount;
    private BigDecimal weightedGain;
    private BigDecimal weightedSell;
    private BigDecimal weightedExternalAmount;
    private BigDecimal adjustRatio;

    //    private BigDecimal tax;
    //    private BigDecimal finalDeduction;

    public static TaxCalculationResultDTO of(
            RiaSellAggregateDTO riaSell,
            BigDecimal weightedExternalAmount,
            BigDecimal adjustRatio) {
        return TaxCalculationResultDTO.builder()
                .originalGainAmount(riaSell.getOriginalGainAmount())
                .weightedGain(riaSell.getWeightedGain())
                .weightedSell(riaSell.getWeightedSell())
                .weightedExternalAmount(weightedExternalAmount)
                .adjustRatio(adjustRatio)
                //                .tax(tax)
                //                .finalDeduction(finalDeduction)
                .build();
    }
}
