package com.app.maria.domain.foreignproduct.dto.response;

import com.app.maria.domain.foreignproduct.dto.ForeignProductDTO;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class ForeignProductResponseDTO {
    private Long foreignProductId;
    private String ticker;
    private String name;
    private String market;
    private String currency;
    private String type;

    public ForeignProductResponseDTO(ForeignProductDTO dto) {
        this.foreignProductId = dto.getForeignProductId();
        this.ticker = dto.getTicker();
        this.name = dto.getName();
        this.market = dto.getMarket();
        this.currency = dto.getCurrency();
        this.type = dto.getType();
    }
}
