package com.app.maria.global.exception;

public class GeneralAccountApiException extends RuntimeException {
    public GeneralAccountApiException(String message) {
        super(message);
    }
    public GeneralAccountApiException(String message, Throwable cause){
        super(message, cause);
    }
}
