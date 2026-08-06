package com.app.maria.domain.sellorder.dto.request;

import com.app.maria.domain.sellorder.dto.SellOrderDTO;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class SellOrderRequestDTO {

    @NotNull(message = "출고 상세 ID를 입력하세요.")
    private Long inboundDetailId;

    @NotNull(message = "매도 수량을 입력하세요.")
    @DecimalMin(value = "0", inclusive = false, message = "매도 수량은 0보다 커야 합니다.")
    private BigDecimal sellQty;

    public SellOrderDTO toSellOrderDTO() {
        return SellOrderDTO.builder()
                .inboundDetailId(inboundDetailId)
                .sellQty(sellQty)
                .build();
    }

}
