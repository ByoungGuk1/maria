package com.app.maria.domain.tax.service;

import com.app.maria.domain.tax.dto.ExternalBuyDTO;
import com.app.maria.domain.tax.dto.RiaSellAggregateDTO;
import com.app.maria.domain.tax.dto.SellLotDTO;
import com.app.maria.domain.tax.dto.TaxCalculationResultDTO;
import com.app.maria.domain.tax.dto.TaxRuleDTO;
import com.app.maria.domain.tax.exception.TaxRuleNotFoundException;
import com.app.maria.domain.tax.type.TaxRuleType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TaxCalculator {

    private static final int RATIO_SCALE = 4;
    private static final int AMOUNT_SCALE = 2;
    private static final int DIVIDE_SCALE = 12;

    public TaxCalculationResultDTO calculate(
            List<SellLotDTO> sellLots,
            List<TaxRuleDTO> taxRules,
            List<ExternalBuyDTO> externalTrades,
            boolean benefitExcluded) {
        RiaSellAggregateDTO riaSell = aggregateRiaSell(sellLots, taxRules);

        BigDecimal weightedExternalAmount = aggregateExternal(externalTrades, taxRules);
        BigDecimal adjustRatio =
                benefitExcluded
                        ? BigDecimal.ZERO.setScale(RATIO_SCALE, RoundingMode.HALF_UP)
                        : adjustRatio(weightedExternalAmount, riaSell.getWeightedSell());
        BigDecimal finalDeduction = findDeduction(riaSell.getWeightedGain(), adjustRatio);
        BigDecimal finalTax = finalTax(riaSell.getOriginalGainAmount(), finalDeduction, taxRules);

        return TaxCalculationResultDTO.of(
                riaSell, weightedExternalAmount, adjustRatio, finalDeduction, finalTax);
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

    private BigDecimal findDeduction(BigDecimal weightedGain, BigDecimal adjustRatio) {
        if (weightedGain.signum() <= 0) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return weightedGain.multiply(adjustRatio).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal finalTax(
            BigDecimal originalGain, BigDecimal finalDeduction, List<TaxRuleDTO> taxRules) {
        BigDecimal taxBase =
                originalGain
                        .subtract(findConstantRule(taxRules, TaxRuleType.BASIC_DEDUCTION))
                        .subtract(finalDeduction);
        if (taxBase.signum() <= 0) {
            return BigDecimal.ZERO.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        }
        return taxBase.multiply(findConstantRule(taxRules, TaxRuleType.TAX_RATE))
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal findWeight(List<TaxRuleDTO> taxRules, LocalDate sellAt) {
        return findRuleValue(taxRules, TaxRuleType.RELIEF_RATE, sellAt)
                .divide(BigDecimal.valueOf(100), RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal findRuleValue(
            List<TaxRuleDTO> taxRules, TaxRuleType ruleType, LocalDate baseDate) {
        return taxRules.stream()
                .filter(rule -> ruleType == rule.getRuleType())
                .filter(
                        rule ->
                                !baseDate.isBefore(rule.getValidFrom())
                                        && !baseDate.isAfter(rule.getValidTo()))
                .findFirst()
                .map(TaxRuleDTO::getRuleValue)
                .orElseThrow(
                        () ->
                                new TaxRuleNotFoundException(
                                        baseDate + " 에 유효한 " + ruleType + " 규칙을 찾지 못했습니다."));
    }

    private BigDecimal findConstantRule(List<TaxRuleDTO> taxRules, TaxRuleType ruleType) {
        return taxRules.stream()
                .filter(rule -> ruleType == rule.getRuleType())
                .findFirst()
                .map(TaxRuleDTO::getRuleValue)
                .orElseThrow(() -> new TaxRuleNotFoundException(ruleType + " 규칙을 찾지 못했습니다."));
    }
}
