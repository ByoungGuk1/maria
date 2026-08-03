package com.app.maria.domain.sellorder.dto;

import com.app.maria.domain.sellorder.dto.response.SellOrderResponseDTO;
import com.app.maria.domain.sellorder.type.SellOrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@EqualsAndHashCode(of = "orderId")
public class SellOrderDTO {

    private Long orderId;
    private Long inboundDetailId;
    private BigDecimal sellQty;
    private BigDecimal basePrice;
    private BigDecimal purchaseFxRate;
    private SellOrderStatus status;
    private LocalDateTime processedAt;

    public SellOrderResponseDTO toResponseDTO() {
        return SellOrderResponseDTO.builder()
                .orderId(orderId)
                .inboundDetailId(inboundDetailId)
                .sellQty(sellQty)
                .basePrice(basePrice)
                .purchaseFxRate(purchaseFxRate)
                .status(status)
                .processedAt(processedAt)
                .build();
    }

}
