package com.app.maria.domain.inbound.dto.response;

import com.app.maria.domain.inbound.dto.InboundListDTO;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class InboundListResponseDTO {
    private Long inboundId;
    private String accountNo;
    private String customerName;
    private String ticker;
    private String productName;
    private String sourceBroker;
    private BigDecimal requestedQty;
    private BigDecimal currentHoldingAtRequest;
    private BigDecimal snapshotQty;
    private BigDecimal approvedQty;
    private BigDecimal remainingQty;
    private LocalDateTime processedAt;

    public InboundListResponseDTO(InboundListDTO dto) {
        this.inboundId = dto.getInboundId();
        this.accountNo = dto.getAccountNo();
        this.customerName = dto.getCustomerName();
        this.ticker = dto.getTicker();
        this.productName = dto.getProductName();
        this.sourceBroker = dto.getSourceBroker();
        this.requestedQty = dto.getRequestedQty();
        this.currentHoldingAtRequest = dto.getCurrentHoldingAtRequest();
        this.snapshotQty = dto.getSnapshotQty();
        this.approvedQty = dto.getApprovedQty();
        this.remainingQty = dto.getRemainingQty();
        this.processedAt = dto.getProcessedAt();
    }
}
