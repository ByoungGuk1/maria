package com.app.maria.domain.foreignproduct.dto;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class ForeignProductDTO {
    private Long foreignProductId;
    private String ticker;
    private String name;
    private String market;
    private String currency;
    private String type;
}
