package com.app.maria.domain.account.exception;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class AccountException extends RuntimeException {
    public AccountException(String message) {
        super(message);
    }
}
