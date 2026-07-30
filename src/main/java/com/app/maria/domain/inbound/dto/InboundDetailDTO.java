package com.app.maria.domain.inbound.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter @ToString @Builder

public class InboundDetailDTO {
    private Long inboundDetailId;
    private Long inboundId;
    private Long foreignProductId;
    private String sourceBroker;
    private Long qty;
    private Long currentQty;
    private LocalDateTime recordedAt;
    private LocalDateTime purchaseDate;
    private BigDecimal purchasePrice;
    private String purchaseCurrency;
    private BigDecimal purchaseFxRate;
}