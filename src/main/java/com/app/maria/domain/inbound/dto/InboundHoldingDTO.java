package com.app.maria.domain.inbound.dto;

import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class InboundHoldingDTO {

    private Long foreignProductId;
    private BigDecimal currentQty;

}
