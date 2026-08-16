package com.app.maria.domain.registrablestock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@ToString
@Builder
public class RegistrableStockResponseDTO {
    private Long generalAccountId;
    private BigDecimal heldQty;
    private String sourceBroker;
    private LocalDateTime purchaseDate;
    private BigDecimal purchasePrice;
    private String purchaseCurrency;
    private BigDecimal purchaseFxRate;
}
