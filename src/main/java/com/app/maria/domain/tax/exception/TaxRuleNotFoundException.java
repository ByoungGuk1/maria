package com.app.maria.domain.tax.exception;

public class TaxRuleNotFoundException extends TaxCalculationException {
    public TaxRuleNotFoundException(String message) {
        super(message);
    }
}
