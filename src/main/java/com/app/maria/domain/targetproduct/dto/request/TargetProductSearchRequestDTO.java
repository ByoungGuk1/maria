package com.app.maria.domain.targetproduct.dto.request;

import com.app.maria.domain.targetproduct.dto.TargetProductSearchDTO;
import com.app.maria.domain.targetproduct.type.StockType;
import com.app.maria.domain.targetproduct.type.TradeType;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class TargetProductSearchRequestDTO {
    private String customerName;
    private StockType stockType;
    private Boolean isTarget;
    private TradeType tradeType;
    private Boolean todayOnly;
    private Boolean inheritanceGiftOnly;

    private int page;

    private int size;

    public TargetProductSearchDTO toTargetProductSearchDTO() {
        return TargetProductSearchDTO.builder()
                .customerName(customerName)
                .stockType(stockType)
                .isTarget(isTarget)
                .tradeType(tradeType)
                .inheritanceGiftOnly(inheritanceGiftOnly)
                .offset(page * size)
                .size(size)
                .build();
    }
}
