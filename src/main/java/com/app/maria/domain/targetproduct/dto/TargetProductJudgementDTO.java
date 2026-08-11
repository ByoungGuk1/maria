package com.app.maria.domain.targetproduct.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class TargetProductJudgementDTO {

    private Long judgementId;
    private Long mydataTradeId;
    private String ciHash;
    private String fundCode;
    private String fundName;
    private Boolean isTarget;
    private BigDecimal foreignStockRatio;
    private LocalDate inceptionDate;
    private LocalDateTime judgedAt;
    private String tradeType;
    private BigDecimal amount;
    private LocalDate tradeDate;
    private BigDecimal netBuyAmount;
}
