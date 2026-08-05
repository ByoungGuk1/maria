package com.app.maria.domain.account.exception;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class AccountNotFoundException extends AccountException {
  public AccountNotFoundException(String message) {
    super(message);
  }
}
