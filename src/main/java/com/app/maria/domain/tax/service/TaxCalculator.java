package com.app.maria.domain.tax.service;

import com.app.maria.domain.tax.dto.ExternalBuyDTO;
import com.app.maria.domain.tax.dto.RiaSellAggregateDTO;
import com.app.maria.domain.tax.dto.SellLotDTO;
import com.app.maria.domain.tax.dto.TaxCalculationResultDTO;
import com.app.maria.domain.tax.dto.TaxRuleDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TaxCalculator {

    private static final String RELIEF_RATE = "RELIEF_RATE";
    private static final String BASIC_DEDUCTION = "BASIC_DEDUCTION";
    private static final String TAX_RATE = "TAX_RATE";
    private static final int RATIO_SCALE = 4;
    private static final int AMOUNT_SCALE = 2;
    private static final int DIVIDE_SCALE = 12;

    public TaxCalculationResultDTO calculate(
            List<SellLotDTO> sellLots,
            List<TaxRuleDTO> taxRules,
            List<ExternalBuyDTO> externalTrades) {
        RiaSellAggregateDTO riaSell = aggregateRiaSell(sellLots, taxRules);

        BigDecimal weightedExternalAmount = aggregateExternal(externalTrades, taxRules);
        BigDecimal adjustRatio = adjustRatio(weightedExternalAmount, riaSell.getWeightedSell());

        return TaxCalculationResultDTO.of(riaSell, weightedExternalAmount, adjustRatio);
    }

    private RiaSellAggregateDTO aggregateRiaSell(List<SellLotDTO> lots, List<TaxRuleDTO> taxRules) {
        BigDecimal weightedSell = BigDecimal.ZERO;
        BigDecimal weightedGain = BigDecimal.ZERO;
        BigDecimal originalGain = BigDecimal.ZERO;

        for (SellLotDTO lot : lots) {
            BigDecimal weight = findWeight(taxRules, lot.getSellAt());

            BigDecimal purchaseCost =
                    lot.getPurchasePrice()
                            .multiply(lot.getPurchaseFxRate())
                            .multiply(lot.getSellQty());
            BigDecimal sellAmount = lot.getFinalAmount();
            BigDecimal gainAmount = sellAmount.subtract(purchaseCost);

            weightedSell = weightedSell.add(sellAmount.multiply(weight));
            weightedGain = weightedGain.add(gainAmount.multiply(weight));
            originalGain = originalGain.add(gainAmount);
        }

        return RiaSellAggregateDTO.of(weightedSell, weightedGain, originalGain);
    }

    private BigDecimal aggregateExternal(
            List<ExternalBuyDTO> externalTrades, List<TaxRuleDTO> taxRules) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ExternalBuyDTO externalTrade : externalTrades) {
            BigDecimal weight = findWeight(taxRules, externalTrade.getTradeDate());
            sum = sum.add(externalTrade.getNetBuyAmount().multiply(weight));
        }
        return sum.max(BigDecimal.ZERO).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal adjustRatio(BigDecimal weightedExternalAmount, BigDecimal weightedSell) {
        if (weightedSell.signum() <= 0) {
            return BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        }

        return BigDecimal.ONE
                .subtract(
                        weightedExternalAmount.divide(
                                weightedSell, DIVIDE_SCALE, RoundingMode.HALF_UP))
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ONE)
                .setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal findWeight(List<TaxRuleDTO> taxRules, LocalDate sellAt) {
        return findRuleValue(taxRules, RELIEF_RATE, sellAt)
                .divide(BigDecimal.valueOf(100), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal findRuleValue(
            List<TaxRuleDTO> taxRules, String ruleType, LocalDate baseDate) {
        return taxRules.stream()
                .filter(rule -> ruleType.equals(rule.getRuleType()))
                .filter(
                        rule ->
                                !baseDate.isBefore(rule.getValidFrom())
                                        && !baseDate.isAfter(rule.getValidTo()))
                .findFirst()
                .map(TaxRuleDTO::getRuleValue)
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        baseDate + " 에 유효한 " + ruleType + " 규칙을 찾지 못했습니다."));
    }
}
