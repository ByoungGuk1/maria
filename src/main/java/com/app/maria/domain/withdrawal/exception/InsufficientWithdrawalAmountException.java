package com.app.maria.domain.withdrawal.exception;

public class InsufficientWithdrawalAmountException extends RuntimeException {
    public InsufficientWithdrawalAmountException(String message) {
        super(message);
    }
}
