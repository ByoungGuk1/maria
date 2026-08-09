package com.app.maria.domain.tax.service;

import com.app.maria.domain.tax.dto.response.TaxCalculationResponseDTO;

public interface TaxCalculationService {
    public TaxCalculationResponseDTO taxCalculate(Long accountId);
}
