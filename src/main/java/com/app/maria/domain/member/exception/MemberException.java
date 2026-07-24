package com.app.maria.domain.member.exception;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class MemberException extends RuntimeException {
  public MemberException(String message) {
    super(message);
  }
}
