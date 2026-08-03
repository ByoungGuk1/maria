package com.app.maria.domain.sellorder.dto;

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

}
