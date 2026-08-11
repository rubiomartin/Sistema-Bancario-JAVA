package com.corebank.external;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;

@Service
public class CoelsaService {

    private static final Map<String, String> ENTIDADES_SOPORTADAS = Map.ofEntries(
            Map.entry("00000031", "Mercado Pago"),
            Map.entry("00000079", "Ualá"),
            Map.entry("00000062", "Prex"),
            Map.entry("00000018", "Personal Pay"),
            Map.entry("017", "BBVA Francés"),
            Map.entry("072", "Banco Santander"),
            Map.entry("011", "Banco Nación"),
            Map.entry("322", "Banco Industrial (BIND)")
    );

    private final SecureRandom random = new SecureRandom();

    public String simular(String identificadorDestino) {
        dormir(300 + random.nextInt(501));

        if (random.nextInt(100) >= 98) {
            throw new RuntimeException("timeout: la cámara compensadora no responde");
        }

        String prefijoCVU = safeSubstring(identificadorDestino, 0, 8);
        String prefijoCBU = safeSubstring(identificadorDestino, 0, 3);
        String entidad = ENTIDADES_SOPORTADAS.getOrDefault(prefijoCVU, ENTIDADES_SOPORTADAS.get(prefijoCBU));

        if (entidad == null) {
            throw new RuntimeException("rechazo COELSA: cuenta destino inexistente o entidad no adherida");
        }

        byte[] buf = new byte[7];
        random.nextBytes(buf);
        return "COELSA-" + HexFormat.of().withUpperCase().formatHex(buf);
    }

    private String safeSubstring(String s, int start, int end) {
        return s.length() < end ? s : s.substring(start, end);
    }

    private void dormir(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
