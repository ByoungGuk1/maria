package com.app.maria.domain.sellorder.exception;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class SellOrderException extends RuntimeException {

    public SellOrderException(String message) {
        super(message);
    }
}
