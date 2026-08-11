package com.app.maria.domain.inbound.dto.request;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class InboundRequestDTO {

    @NotNull(message = "accountId는 필수입니다.")
    private Long accountId;

    @NotNull(message = "foreignProductId는 필수입니다.")
    private Long foreignProductId;

    @NotNull(message = "requestedQty는 필수입니다.")
    private BigDecimal requestedQty;

    private BigDecimal currentHoldingAtRequest;
}
