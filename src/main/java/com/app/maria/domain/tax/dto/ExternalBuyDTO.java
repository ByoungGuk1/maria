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
public class ExternalBuyDTO {
    private Long accountId;
    private LocalDate tradeDate;
    private BigDecimal netBuyAmount;
    // 관리자가 "어느 종목을 밖에서 다시 샀는지" 볼 수 있도록 표시용으로만 들고 다닌다.
    private String productLabel;
}
