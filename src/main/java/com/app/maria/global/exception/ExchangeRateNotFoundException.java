package com.app.maria.global.exception;

public class ExchangeRateNotFoundException extends RuntimeException {

    public ExchangeRateNotFoundException(String message) {
        super(message);
    }

    public ExchangeRateNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}
