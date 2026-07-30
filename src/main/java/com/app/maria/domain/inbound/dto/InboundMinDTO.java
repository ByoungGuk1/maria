package com.app.maria.domain.inbound.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter @ToString @Builder

public class InboundMinDTO {
    private Long inboundMinId;
    private Long inboundDetailId;
    private Long requestedQty;
    private Long approvedQty;
    private Long snapshotQty;
}
