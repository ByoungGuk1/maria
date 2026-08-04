package com.app.maria.domain.account.exception;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class DuplicateAccountException extends AccountException {
  public DuplicateAccountException(String message) {
    super(message);
  }
}
