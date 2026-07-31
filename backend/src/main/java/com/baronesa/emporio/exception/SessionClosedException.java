package com.baronesa.emporio.exception;

public class SessionClosedException extends RuntimeException {
    public SessionClosedException(String message) {
        super(message);
    }
}

