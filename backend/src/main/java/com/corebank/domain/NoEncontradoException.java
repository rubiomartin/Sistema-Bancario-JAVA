package com.corebank.domain;

public class NoEncontradoException extends RuntimeException {
    public NoEncontradoException() {
        super("no encontrado");
    }
}
