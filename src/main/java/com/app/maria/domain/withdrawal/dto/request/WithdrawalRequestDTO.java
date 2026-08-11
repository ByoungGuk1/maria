package com.app.maria.domain.withdrawal.dto.request;

import java.math.BigDecimal;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class WithdrawalRequestDTO {
    private Long accountId;
    private BigDecimal requestedAmount;
    private String destinationAccountNo;
    // 조기인출 동의 여부(default=false)
    private boolean earlyWithdrawalAgreed;
}
