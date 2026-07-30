package com.app.maria.domain.domestic.dto;

import com.app.maria.domain.domestic.type.Status;
import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter @ToString @Builder
public class DomesticStockBalanceDTO {
    private Long domesticStockBalanceId;
    private Long accountId;
    private Long domesticProductId;
    private BigDecimal qty;
    private Status status;
}