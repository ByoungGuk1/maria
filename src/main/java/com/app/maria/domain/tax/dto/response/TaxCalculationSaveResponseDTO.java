package com.app.maria.domain.tax.dto.response;

import com.app.maria.domain.tax.dto.TaxCalculationDTO;
import com.app.maria.domain.tax.type.TaxBasisType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TaxCalculationSaveResponseDTO {
    private Long calcId;
    private Long accountId;
    private TaxBasisType basisType;
    private LocalDateTime calculatedAt;
    private BigDecimal sellAmount;
    private BigDecimal gainAmount;
    private BigDecimal gainWeighted;
    private BigDecimal extAmount;
    private BigDecimal ratio;
    private BigDecimal deduction;
    private BigDecimal tax;

    public static TaxCalculationSaveResponseDTO of(TaxCalculationDTO calculation) {
        return TaxCalculationSaveResponseDTO.builder()
                .calcId(calculation.getCalcId())
                .accountId(calculation.getAccountId())
                .basisType(calculation.getBasisType())
                .calculatedAt(calculation.getCalculatedAt())
                .sellAmount(calculation.getSellAmount())
                .gainAmount(calculation.getGainAmount())
                .gainWeighted(calculation.getGainWeighted())
                .extAmount(calculation.getExtAmount())
                .ratio(calculation.getRatio())
                .deduction(calculation.getDeduction())
                .tax(calculation.getTax())
                .build();
    }
}
