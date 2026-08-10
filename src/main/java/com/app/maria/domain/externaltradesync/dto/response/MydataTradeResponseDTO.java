package com.app.maria.domain.externaltradesync.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode(of = "tradeId")
public class MydataTradeResponseDTO {

    private Long tradeId;
    private String ciHash;
    private String brokerName;
    private String tradeType;
    private String stockType;
    private BigDecimal qty;
    private LocalDate tradeDate;
    private BigDecimal amount;
    private String fundCode;
}
