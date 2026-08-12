package com.app.maria.domain.targetproduct.dto.response;

import com.app.maria.domain.targetproduct.dto.TargetProductJudgementListDTO;
import com.app.maria.domain.targetproduct.type.StockType;
import com.app.maria.domain.targetproduct.type.TradeType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter @ToString @Builder
public class TargetProductJudgementListResponseDTO {
    private Long judgementId;
    private String customerName;
    private StockType stockType;
    private String fundName;
    private String ticker;
    private Boolean isTarget;
    private TradeType tradeType;
    private BigDecimal amount;
    private BigDecimal netBuyAmount;
    private LocalDate tradeDate;
    private LocalDateTime judgedAt;

    public TargetProductJudgementListResponseDTO(TargetProductJudgementListDTO dto) {
        this.judgementId = dto.getJudgementId();
        this.customerName = dto.getCustomerName();
        this.stockType = dto.getStockType();
        this.fundName = dto.getFundName();
        this.ticker = dto.getTicker();
        this.isTarget = dto.getIsTarget();
        this.tradeType = dto.getTradeType();
        this.amount = dto.getAmount();
        this.netBuyAmount = dto.getNetBuyAmount();
        this.tradeDate = dto.getTradeDate();
        this.judgedAt = dto.getJudgedAt();
    }
}
