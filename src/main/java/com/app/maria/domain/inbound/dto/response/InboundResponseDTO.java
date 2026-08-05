package com.app.maria.domain.inbound.dto.response;

import com.app.maria.domain.inbound.dto.InboundDTO;
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

    public static InboundResponseDTO of(
            InboundDTO inboundDTO,
            BigDecimal snapshotQty) {
        return InboundResponseDTO.builder()
                .inboundId(inboundDTO.getInboundId())
                .requestedQty(inboundDTO.getRequestedQty())
                .snapshotQty(snapshotQty)
                .currentHoldingAtRequest(inboundDTO.getCurrentHoldingAtRequest())
                .approvedQty(inboundDTO.getApprovedQty())
                .processedAt(inboundDTO.getProcessedAt())
                .build();
    }
}
