package com.app.maria.domain.account.dto.response;

import com.app.maria.domain.account.dto.AccountStatusLogDTO;
import com.app.maria.domain.account.type.Status;
import lombok.*;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@EqualsAndHashCode(of = "logId")
public class AccountLogResponseDTO{
  private Long logId;
  private Long accountId;
  private Status prevStatus;
  private Status newStatus;
  private LocalDateTime changedAt;
  private String reason;

  public AccountLogResponseDTO(AccountStatusLogDTO logDTO) {
    this.logId = logDTO.getLogId()!=null?logDTO.getLogId():null;
    this.accountId =  logDTO.getAccountId()!=null?logDTO.getAccountId():null;
    this.prevStatus = logDTO.getPrevStatus()!=null?logDTO.getPrevStatus():null;
    this.newStatus = logDTO.getNewStatus()!=null?logDTO.getNewStatus():null;
    this.changedAt = logDTO.getChangedAt()!=null?logDTO.getChangedAt():null;
    this.reason = logDTO.getReason()!=null?logDTO.getReason():null;
  }
}