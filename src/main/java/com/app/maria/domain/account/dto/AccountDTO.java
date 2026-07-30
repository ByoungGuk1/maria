package com.app.maria.domain.account.dto;

import com.app.maria.domain.account.dto.request.AccountRequestDTO;
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

  public AccountDTO(AccountRequestDTO accountRequestDTO) {
      this.accountId = accountRequestDTO.getAccountId() != null? accountRequestDTO.getAccountId(): null;
      this.customerId = accountRequestDTO.getCustomerId()  != null? accountRequestDTO.getCustomerId(): null;
      this.status = accountRequestDTO.getStatus() != null? accountRequestDTO.getStatus(): null;
      this.openedAt = null;
      this.createdAt = null;
      this.accountNo = accountRequestDTO.getAccountNo() != null? accountRequestDTO.getAccountNo(): null;
      this.limitAmount = accountRequestDTO.getLimitAmount() != null? accountRequestDTO.getLimitAmount(): null;
      this.amount = accountRequestDTO.getAmount() != null? accountRequestDTO.getAmount(): null;
  }
}
