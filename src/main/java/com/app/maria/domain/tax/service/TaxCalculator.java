package com.app.maria.domain.tax.service;

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

    public TaxCalculationResultDTO calculate(List<SellLotDTO> lots, List<TaxRuleDTO> rules ) {
        RiaSellAggregateDTO riaSellAggregate = aggregateRiaSell(lots, rules);

        return TaxCalculationResultDTO.of(riaSellAggregate);
    }
    private RiaSellAggregateDTO aggregateRiaSell(List<SellLotDTO> lots, List<TaxRuleDTO> weights) {
        BigDecimal weightedSell = BigDecimal.ZERO;
        BigDecimal weightedGain = BigDecimal.ZERO;
        BigDecimal originalGain = BigDecimal.ZERO;

        for (SellLotDTO lot : lots) {
            BigDecimal weight = findWeight(weights, lot.getSellAt());

            BigDecimal purchaseCost = lot.getPurchasePrice()
                    .multiply(lot.getPurchaseFxRate())
                    .multiply(lot.getSellQty());
            BigDecimal sellAmount = lot.getFinalAmount();
            BigDecimal gainAmount = sellAmount.subtract(purchaseCost);

            weightedSell = weightedSell.add(sellAmount.multiply(weight));
            weightedGain = weightedGain.add(gainAmount.multiply(weight));
            originalGain = originalGain.add(gainAmount);
        }

        return  RiaSellAggregateDTO.of(weightedSell,weightedGain,originalGain);
    }

    private BigDecimal findWeight(List<TaxRuleDTO> rules,LocalDate sellAt) {
        return findRuleValue(rules, RELIEF_RATE, sellAt)
                .divide(BigDecimal.valueOf(100),4, RoundingMode.HALF_UP);
    }

    private BigDecimal findRuleValue(List<TaxRuleDTO> rules, String ruleType, LocalDate baseDate) {
        return rules.stream()
                .filter(rule -> ruleType.equals(rule.getRuleType()))
                .filter(rule -> !baseDate.isBefore(rule.getValidFrom()) && !baseDate.isAfter(rule.getValidTo()))
                .findFirst()
                .map(TaxRuleDTO::getRuleValue)
                .orElseThrow(() -> new IllegalArgumentException(
                        baseDate + " 에 유효한 " + ruleType + " 규칙을 찾지 못했습니다."));
    }
}
