package com.app.maria.domain.inbound.exception;

public class InboundNotFoundException extends RuntimeException {

    public InboundNotFoundException(String message) {
        super(message);
    }
}
