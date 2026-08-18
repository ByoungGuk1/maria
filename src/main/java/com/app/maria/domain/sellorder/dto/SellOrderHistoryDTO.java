package com.app.maria.domain.sellorder.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class SellOrderHistoryDTO {

    private String accountNo;
    private String customerName;
    private String ticker;
    private String name;
    private BigDecimal sellQty;
    private BigDecimal basePrice;
    private LocalDateTime processedAt;
    private BigDecimal provisionalAmount;
    private BigDecimal finalAmount;
    private String status;

}
