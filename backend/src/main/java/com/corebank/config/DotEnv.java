package com.corebank.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DotEnv {

    private DotEnv() {
    }

    public static void load(String path) {
        Path archivo = Path.of(path);
        if (!Files.isReadable(archivo)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(archivo)) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) {
                    continue;
                }
                int idx = linea.indexOf('=');
                if (idx < 0) {
                    continue;
                }
                String clave = linea.substring(0, idx).trim();
                String valor = linea.substring(idx + 1).trim().replaceAll("^['\"]|['\"]$", "");
                if (System.getProperty(clave) == null && System.getenv(clave) == null) {
                    System.setProperty(clave, valor);
                }
            }
        } catch (IOException e) {

        }
    }
}
