package com.app.maria.domain.inbound.dto.request;

import lombok.*;
import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter @ToString @Builder
public class InboundRequestDTO {
    private Long accountId;
    private Long foreignProductId;
    private BigDecimal requestedQty;
}
