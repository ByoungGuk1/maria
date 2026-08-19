package com.app.maria.domain.domestic.dto.request;

import com.app.maria.domain.domestic.dto.DomesticInvestmentSearchDTO;
import com.app.maria.domain.domestic.type.DomesticStockStatus;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class DomesticInvestmentSearchRequestDTO {
    private String keyword;
    private DomesticStockStatus status;
    private Boolean hasRestrictedHolding;
    private Boolean hasUnpurchasableHolding;
    private Boolean hasRecentBuy;
    private Integer recentBuyDays;
    private int page;
    private int size;

    public DomesticInvestmentSearchDTO toDomesticInvestmentSearchDTO() {
        return DomesticInvestmentSearchDTO.builder()
                .keyword(keyword)
                .status(status)
                .hasRestrictedHolding(hasRestrictedHolding)
                .hasRecentBuy(hasRecentBuy)
                .offset(page * size)
                .size(size)
                .build();
    }
}
