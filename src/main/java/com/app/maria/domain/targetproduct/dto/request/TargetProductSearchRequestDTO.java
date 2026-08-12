package com.app.maria.domain.targetproduct.dto.request;

import com.app.maria.domain.targetproduct.dto.TargetProductSearchDTO;
import com.app.maria.domain.targetproduct.type.StockType;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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

    @PositiveOrZero private int page;

    @Positive private int size;

    public TargetProductSearchDTO toTargetProductSearchDTO() {
        return TargetProductSearchDTO.builder()
                .customerName(customerName)
                .stockType(stockType)
                .isTarget(isTarget)
                .offset(page * size)
                .size(size)
                .build();
    }
}
