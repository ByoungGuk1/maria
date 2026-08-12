package com.app.maria.domain.tax.service;

import com.app.maria.domain.tax.dto.response.TaxCalculationPreviewResponseDTO;
import com.app.maria.domain.tax.dto.response.TaxCalculationSaveResponseDTO;

public interface TaxCalculationService {
    TaxCalculationPreviewResponseDTO taxCalculate(Long accountId);

    TaxCalculationSaveResponseDTO calculateAndSave(Long accountId);
}
