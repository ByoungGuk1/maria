package com.app.maria.domain.sellorder.dto.request;

import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class SellOrderRequestDTO {
    private Long inboundDetailId;
    private BigDecimal sellQty;

}
