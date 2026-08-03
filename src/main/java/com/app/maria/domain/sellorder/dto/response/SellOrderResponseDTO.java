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
        this.orderId = dto.getOrderId() != null ? dto.getOrderId() : null;
        this.inboundDetailId = dto.getInboundDetailId() != null ? dto.getInboundDetailId() : null;
        this.sellQty = dto.getSellQty() != null ? dto.getSellQty() : null;
        this.basePrice = dto.getBasePrice() != null ? dto.getBasePrice() : null;
        this.purchaseFxRate = dto.getPurchaseFxRate() != null ? dto.getPurchaseFxRate() : null;
        this.status = dto.getStatus() != null ? dto.getStatus() : null;
        this.processedAt = dto.getProcessedAt() != null ? dto.getProcessedAt() : null;
    }

}
