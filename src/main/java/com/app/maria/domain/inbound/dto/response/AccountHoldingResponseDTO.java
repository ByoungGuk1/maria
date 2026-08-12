package com.app.maria.domain.inbound.dto.response;

import com.app.maria.domain.foreignproduct.dto.ForeignProductDTO;
import lombok.*;

import java.math.BigDecimal;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class AccountHoldingResponseDTO {
    private Long foreignProductId;
    private String ticker;
    private String name;
    private String market;
    private String currency;
    private String type;   // ForeignProductDTO.type과 동일 enum 확인 필요
    private BigDecimal currentQty;

    public AccountHoldingResponseDTO(ForeignProductDTO product, BigDecimal currentQty) {
        this.foreignProductId = product.getForeignProductId();
        this.ticker = product.getTicker();
        this.name = product.getName();
        this.market = product.getMarket();
        this.currency = product.getCurrency();
        this.type = product.getType();
        this.currentQty = currentQty;
    }

}
