package com.app.maria.domain.account.type;

import lombok.Getter;

@Getter
public enum AutomaticRejectionReason {
  RESIDENCY_UNVERIFIED("거주자 여부를 확인할 수 없습니다."),
  IDENTITY_DATA_MISMATCH("본인확인 정보가 일치하지 않습니다."),
  EXTERNAL_LIMIT_DATA_MISMATCH("타 금융회사 한도 정보가 일치하지 않습니다.");

  private final String message;

  AutomaticRejectionReason(String message) {
    this.message = message;
  }

  public String toLogReason() {
    return "[" + name() + "] " + message;
  }
}
