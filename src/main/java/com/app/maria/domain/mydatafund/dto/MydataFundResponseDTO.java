package com.app.maria.domain.mydatafund.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MydataFundResponseDTO {

    private String fundCode;
    private String fundName;
    private Double foreignStockRatio;
    private LocalDate inceptionDate;
}
