package com.app.maria.domain.tax.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SellLotDTO {
    private Long accountId;
    private Long orderId;
    private Long inboundDetailId;
    private BigDecimal purchaseFxRate;
    private BigDecimal purchasePrice;
    private BigDecimal sellQty;
    private LocalDate sellAt;
    private BigDecimal finalAmount;

    public static SellLotDTO of(
            Long orderId,
            Long inboundDetailId,
            BigDecimal purchaseFxRate,
            BigDecimal purchasePrice,
            BigDecimal sellQty,
            LocalDate sellAt,
            BigDecimal finalAmount) {
        return SellLotDTO.builder()
                .orderId(orderId)
                .inboundDetailId(inboundDetailId)
                .purchaseFxRate(purchaseFxRate)
                .purchasePrice(purchasePrice)
                .sellQty(sellQty)
                .sellAt(sellAt)
                .finalAmount(finalAmount)
                .build();
    }
}
// 매도 (수량×단가×매도시 환율)	매도금액(원화)
// 취득 (수량×단가×매수당시환율)
// 취득원가(원화)	양도소득
