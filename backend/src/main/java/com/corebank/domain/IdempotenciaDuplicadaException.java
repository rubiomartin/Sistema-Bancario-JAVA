package com.corebank.domain;

public class IdempotenciaDuplicadaException extends RuntimeException {
    public IdempotenciaDuplicadaException() {
        super("operación duplicada");
    }
}
