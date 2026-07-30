package com.app.maria.domain.withdrawal.dto;

import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class WithdrawalAllocationDTO {
    private Long allocationId;
    private Long withdrawalId;
    private Long exchangeId;
    private BigDecimal allocatedAmount;
}
