package com.app.maria.domain.inbound.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundLotDTO {
    private Long inboundId;
    private Long inboundDetailId;
    private String sourceBroker;
    private BigDecimal qty;
    private BigDecimal currentQty;
    private LocalDateTime recordedAt;
    private LocalDateTime purchaseDate;
    private BigDecimal purchasePrice;
    private String purchaseCurrency;
    private BigDecimal purchaseFxRate;
    private List<InboundSellHistoryDTO> sellHistory;
}
