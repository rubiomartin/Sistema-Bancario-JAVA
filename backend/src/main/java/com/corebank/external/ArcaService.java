package com.corebank.external;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class ArcaService {

    private final SecureRandom random = new SecureRandom();

    public String simular() {
        dormir(500 + random.nextInt(701));
        long numero = (long) (random.nextDouble() * 1_000_000_000_000L);
        return String.format("73%012d", numero);
    }

    private void dormir(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
