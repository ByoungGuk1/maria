package com.app.maria.domain.account.dto;

import com.app.maria.domain.account.dto.response.AccountResponseDTO;
import com.app.maria.domain.account.type.Status;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@EqualsAndHashCode(of = "accountId")
public class AccountDTO {
  private Long accountId;
  private Long customerId;
  private Status status;
  private LocalDateTime openedAt;
  private LocalDateTime createdAt;
  private BigDecimal accountNo;
  private BigDecimal limitAmount;
  private BigDecimal amount;

  public AccountResponseDTO toResponseDTO() {
    return new AccountResponseDTO(this);
  }
}
