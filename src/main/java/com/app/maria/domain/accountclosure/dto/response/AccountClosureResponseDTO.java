package com.app.maria.domain.accountclosure.dto.response;

import com.app.maria.domain.accountclosure.dto.AccountClosureDTO;
import com.app.maria.domain.accountclosure.type.AccountClosureStatus;
import java.time.LocalDateTime;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class AccountClosureResponseDTO {
    private Long closureRequestId;
    private Long accountId;
    private Long destinationGeneralAccountId;
    private boolean earlyWithdrawalAgreed;
    private AccountClosureStatus status;
    private LocalDateTime requestedAt;

    public static AccountClosureResponseDTO from(AccountClosureDTO closure) {
        return AccountClosureResponseDTO.builder()
                .closureRequestId(closure.getClosureRequestId())
                .accountId(closure.getAccountId())
                .destinationGeneralAccountId(closure.getDestinationGeneralAccountId())
                .earlyWithdrawalAgreed(closure.isEarlyWithdrawalAgreed())
                .status(closure.getStatus())
                .requestedAt(closure.getRequestedAt())
                .build();
    }
}
