package com.app.maria.domain.tax.dto;

import java.math.BigDecimal;
import java.util.List;
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
    private BigDecimal finalDeduction;
    private BigDecimal finalTax;
    private List<TaxPeriodBreakdownDTO> periodBreakdown;
    private List<TaxLotDetailDTO> sellLotDetails;
    private List<TaxExternalTradeDetailDTO> externalTradeDetails;

    public static TaxCalculationResultDTO of(
            RiaSellAggregateDTO riaSell,
            BigDecimal weightedExternalAmount,
            BigDecimal adjustRatio,
            BigDecimal finalDeduction,
            BigDecimal finalTax,
            List<TaxPeriodBreakdownDTO> periodBreakdown,
            List<TaxLotDetailDTO> sellLotDetails,
            List<TaxExternalTradeDetailDTO> externalTradeDetails) {
        return TaxCalculationResultDTO.builder()
                .originalGainAmount(riaSell.getOriginalGainAmount())
                .weightedGain(riaSell.getWeightedGain())
                .weightedSell(riaSell.getWeightedSell())
                .weightedExternalAmount(weightedExternalAmount)
                .adjustRatio(adjustRatio)
                .finalDeduction(finalDeduction)
                .finalTax(finalTax)
                .periodBreakdown(periodBreakdown)
                .sellLotDetails(sellLotDetails)
                .externalTradeDetails(externalTradeDetails)
                .build();
    }
}
