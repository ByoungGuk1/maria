package com.app.maria.domain.account.dto.request;

import com.app.maria.domain.account.type.Status;
import lombok.*;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@EqualsAndHashCode(of = "accountId")
public class AccountRequestDTO {
  private Long accountId;
  private Long customerId;
  private Status status;
  private BigDecimal accountNo;
  private BigDecimal limitAmount;
  private BigDecimal amount;
}
