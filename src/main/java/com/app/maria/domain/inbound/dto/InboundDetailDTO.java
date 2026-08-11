package com.app.maria.domain.inbound.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class InboundDetailDTO {
    private Long inboundDetailId;
    private Long inboundId;
    private Long foreignProductId;
    private String sourceBroker;
    private BigDecimal qty;
    private BigDecimal currentQty;
    private LocalDateTime recordedAt;
    private LocalDateTime purchaseDate;
    private BigDecimal purchasePrice;
    private String purchaseCurrency;
    private BigDecimal purchaseFxRate;
    private Long sourceGeneralAccountId;
}
