package com.app.maria.domain.sellorder.dto.response;

import com.app.maria.domain.sellorder.dto.SellOrderDTO;
import com.app.maria.domain.sellorder.type.SellOrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class SellOrderResponseDTO {

    private Long orderId;
    private Long inboundDetailId;
    private BigDecimal sellQty;
    private BigDecimal basePrice;
    private BigDecimal purchaseFxRate;
    private SellOrderStatus status;
    private LocalDateTime processedAt;

    public SellOrderResponseDTO(SellOrderDTO dto) {
        this.orderId = dto.getOrderId();
        this.inboundDetailId = dto.getInboundDetailId();
        this.sellQty = dto.getSellQty();
        this.basePrice = dto.getBasePrice();
        this.purchaseFxRate = dto.getPurchaseFxRate();
        this.status = dto.getStatus();
        this.processedAt = dto.getProcessedAt();
    }

}
