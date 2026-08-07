package com.app.maria.domain.withdrawal.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class LeftAmountDTO {
    private Long leftAmountId;
    private Long exchangeId;
    private BigDecimal curAmount;
    private LocalDateTime finalAt;
}
