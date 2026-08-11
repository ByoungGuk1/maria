package com.app.maria.domain.withdrawal.exception;

public class EarlyWithdrawalConsentRequiredException extends WithdrawalException {
    public EarlyWithdrawalConsentRequiredException(String message) {
        super(message);
    }
}
