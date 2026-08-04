package com.app.maria.domain.account.dto.response;

import com.app.maria.domain.account.dto.AccountBenefitLogDTO;
import com.app.maria.domain.account.type.BenefitType;
import lombok.*;

import java.time.LocalDateTime;
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@ToString
@Builder
@EqualsAndHashCode(of = "benefitId")
public class AccountBenefitResponseDTO {
  private Long benefitId;
  private Long accountId;
  private BenefitType prevStatus;
  private BenefitType newStatus;
  private LocalDateTime changedAt;
  private String reason;

  public AccountBenefitResponseDTO(AccountBenefitLogDTO logDTO) {
    this.benefitId = logDTO.getBenefitId()!=null?logDTO.getBenefitId():null;
    this.accountId =  logDTO.getAccountId()!=null?logDTO.getAccountId():null;
    this.prevStatus = logDTO.getPrevStatus()!=null?logDTO.getPrevStatus():null;
    this.newStatus = logDTO.getNewStatus()!=null?logDTO.getNewStatus():null;
    this.changedAt = logDTO.getChangedAt()!=null?logDTO.getChangedAt():null;
    this.reason = logDTO.getReason()!=null?logDTO.getReason():null;
  }
}
