package com.app.maria.domain.tax.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 관리자가 "밖에서 어느 종목을 다시 샀는지" 볼 수 있도록, 외부 매수 건 하나하나를 표시용으로 넘긴다.
@Getter
@Builder
@AllArgsConstructor
public class TaxExternalTradeDetailDTO {
    private String productLabel;
    private LocalDate tradeDate;
    private BigDecimal netBuyAmount;
}
