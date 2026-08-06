package com.app.maria.domain.foreignproduct.dto.response;

import com.app.maria.domain.foreignproduct.dto.ForeignProductDTO;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter @Setter @ToString @Builder
public class ForeignProductResponseDTO {
    private Long foreignProductId;
    private String ticker;
    private String name;
    private String market;
    private String currency;
    private String type;

    public static ForeignProductResponseDTO of(ForeignProductDTO dto) {
        return ForeignProductResponseDTO.builder()
                .foreignProductId(dto.getForeignProductId())
                .ticker(dto.getTicker())
                .name(dto.getName())
                .market(dto.getMarket())
                .currency(dto.getCurrency())
                .type(dto.getType())
                .build();
    }
}