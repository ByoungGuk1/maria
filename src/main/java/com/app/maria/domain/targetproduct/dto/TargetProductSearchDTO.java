package com.app.maria.domain.targetproduct.dto;

import com.app.maria.domain.targetproduct.type.StockType;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class TargetProductSearchDTO {
    private String customerName;
    private StockType stockType;
    private Boolean isTarget;
    private int offset;
    private int size;
}
