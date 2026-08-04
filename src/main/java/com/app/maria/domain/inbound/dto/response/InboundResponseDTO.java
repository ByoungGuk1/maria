package com.app.maria.domain.inbound.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter @ToString @Builder
public class InboundResponseDTO {
    private Long inboundId;
    private BigDecimal requestedQty;
    private BigDecimal snapshotQty;
    private BigDecimal currentHoldingAtRequest;
    private BigDecimal approvedQty;
    private LocalDateTime processedAt;
}
