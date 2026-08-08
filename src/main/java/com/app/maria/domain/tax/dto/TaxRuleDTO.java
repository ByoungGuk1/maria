package com.app.maria.domain.tax.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TaxRuleDTO {
    private Long ruleId;
    private BigDecimal ruleValue;
    private LocalDate validTo;
    private LocalDate validFrom;
}
