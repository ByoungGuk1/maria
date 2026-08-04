package com.app.maria.domain.sellorder.dto.request;

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

    // inbound_detail DTO 연동 전 임시 필드
    @NotNull(message = "거래소 코드는 필수입니다.")
    private String exchangeCode;

    @NotNull(message = "종목 코드는 필수입니다.")
    private String ticker;

    @NotNull(message = "통화 단위는 필수입니다.")
    private String currencyUnit;


}
