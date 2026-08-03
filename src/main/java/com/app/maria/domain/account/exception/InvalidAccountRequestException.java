package com.app.maria.domain.account.exception;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class InvalidAccountRequestException extends AccountException {
  public InvalidAccountRequestException(String message) {
    super(message);
  }
}
