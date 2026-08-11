package com.corebank.domain;

public final class Money {

    private Money() {
    }

    public static long parseAmountToCents(String raw) {
        try {
            double valor = Double.parseDouble(raw);
            return Math.round(valor * 100);
        } catch (NumberFormatException | NullPointerException e) {
            throw new IllegalArgumentException("monto inválido: " + raw, e);
        }
    }

    public static String centsToDecimalString(long cents) {
        boolean negativo = cents < 0;
        long abs = Math.abs(cents);
        String signo = negativo ? "-" : "";
        return String.format("%s%d.%02d", signo, abs / 100, abs % 100);
    }
}
