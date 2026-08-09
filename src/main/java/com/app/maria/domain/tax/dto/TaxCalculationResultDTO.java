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
//    private BigDecimal adjustRatio;
//    private BigDecimal tax;
//    private BigDecimal finalDeduction;
//    private BigDecimal externalPurchaseWeighted;

    public static TaxCalculationResultDTO of(
            RiaSellAggregateDTO riaSellAggregate) {
        return TaxCalculationResultDTO.builder()
                .originalGainAmount(riaSellAggregate.getOriginalGainAmount())
                .weightedGain(riaSellAggregate.getWeightedGain())
                .weightedSell(riaSellAggregate.getWeightedSell())
//                .adjustRatio(adjustRatio)
//                .tax(tax)
//                .finalDeduction(finalDeduction)
//                .externalPurchaseWeighted(externalPurchaseWeighted)
                .build();
    }

}
