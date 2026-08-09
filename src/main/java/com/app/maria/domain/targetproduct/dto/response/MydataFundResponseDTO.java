package com.app.maria.domain.targetproduct.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
public class MydataFundResponseDTO {

    private String fundCode;
    private String fundName;
    private BigDecimal foreignStockRatio;
    private LocalDate inceptionDate;
}
