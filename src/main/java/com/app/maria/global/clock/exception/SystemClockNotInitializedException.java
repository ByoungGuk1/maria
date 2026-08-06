package com.app.maria.global.clock.exception;

public class SystemClockNotInitializedException extends RuntimeException {
    public SystemClockNotInitializedException(String message) {
        super(message);
    }
}
