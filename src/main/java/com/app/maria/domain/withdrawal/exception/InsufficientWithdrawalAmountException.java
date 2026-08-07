package com.app.maria.domain.withdrawal.exception;

public class InsufficientWithdrawalAmountException extends WithdrawalException {
    public InsufficientWithdrawalAmountException(String message) {
        super(message);
    }
}
