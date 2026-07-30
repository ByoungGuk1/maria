package com.app.maria.domain.withdrawal.dto;

import com.app.maria.domain.withdrawal.type.WithdrawalType;
import lombok.*;

import java.math.BigDecimal;
import java.util.Date;
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class WithdrawalDTO {
    private Long withdrawalId;
    private Long accountId;
    private BigDecimal requestedAmount;
    private WithdrawalType type;
    private Date processedAt;
    private String destinationBank;
    private String destinationAccount_no;
}
