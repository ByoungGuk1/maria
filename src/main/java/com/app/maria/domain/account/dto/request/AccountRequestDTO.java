package com.app.maria.domain.account.dto.request;

import com.app.maria.domain.account.dto.AccountDTO;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class AccountRequestDTO {
    @NotNull(message = "개설할 계좌의 사용자 정보는 필수입니다.")
    @Positive(message = "사용자 ID는 0보다 커야 합니다.")
    private Long customerId;

    @NotNull(message = "계좌 한도는 필수입니다.")
    @DecimalMin(value = "1", message = "계좌 한도는 1원 이상이어야 합니다.")
    @DecimalMax(value = "50000000", message = "계좌 한도는 5천만원 이하여야 합니다.")
    @Digits(integer = 8, fraction = 0, message = "계좌 한도는 원 단위 정수로 입력해야 합니다.")
    private BigDecimal limitAmount;

    public AccountDTO toAccountDTO() {
        return AccountDTO.builder().customerId(customerId).limitAmount(limitAmount).build();
    }
}
