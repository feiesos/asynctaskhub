package org.feiesos.asynctaskhub.service;

public class NonRetryableException extends RuntimeException {

    public NonRetryableException(String message) {
        super(message);
    }
}
