package com.corebank.web;

final class TokenBucket {

    private final double tokensPerSecond;
    private final double capacidad;
    private double tokens;
    private long ultimaActualizacionNanos;

    TokenBucket(double tokensPerSecond, double burst) {
        this.tokensPerSecond = tokensPerSecond;
        this.capacidad = burst;
        this.tokens = burst;
        this.ultimaActualizacionNanos = System.nanoTime();
    }

    synchronized boolean tryAcquire() {
        long ahora = System.nanoTime();
        double transcurridoSeg = (ahora - ultimaActualizacionNanos) / 1_000_000_000.0;
        ultimaActualizacionNanos = ahora;

        tokens = Math.min(capacidad, tokens + transcurridoSeg * tokensPerSecond);

        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }
}
