package com.app.maria.domain.tax.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

// 관리자가 "왜 조정비율이 이렇게 나왔는지" 볼 수 있도록, 최종 합산 전 구간(1~5월/6~7월/8~12월)별 값을 보존한다.
@Getter
@Builder
@AllArgsConstructor
public class TaxPeriodBreakdownDTO {
    private LocalDate validFrom;
    private LocalDate validTo;
    private BigDecimal weight;
    private BigDecimal sellAmount;
    private BigDecimal gainAmount;
    private BigDecimal externalNetBuyAmount;
}
