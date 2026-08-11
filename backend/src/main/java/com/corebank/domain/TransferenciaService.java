package com.corebank.domain;

import com.corebank.external.ArcaService;
import com.corebank.external.CoelsaService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TransferenciaService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CoelsaService coelsa;
    private final ArcaService arca;

    public TransferenciaService(JdbcTemplate jdbc, PlatformTransactionManager txManager,
                                 CoelsaService coelsa, ArcaService arca) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.coelsa = coelsa;
        this.arca = arca;
    }

    public record Entrada(String usuarioOrigen, String usuarioDestino, String montoRaw, String idIdempotencia) {
    }

    public record Auditoria(String idOperacion, String idCoelsa, String caeAfip) {
    }

    public record Desglose(String netoTransferido, String comisionFintech, String ivaRecaudado) {
    }

    public record Resultado(boolean ok, String mensaje, String saldoRestante,
                             Auditoria auditoria, Desglose desglose, boolean usuarioActivo) {
        static Resultado rechazo(String mensaje) {
            return new Resultado(false, mensaje, null, null, null, false);
        }

        static Resultado rechazo(String mensaje, String saldoRestante) {
            return new Resultado(false, mensaje, saldoRestante, null, null, false);
        }
    }

    public Resultado registrarTransferencia(Entrada in) {
        long montoCents;
        try {
            montoCents = Money.parseAmountToCents(in.montoRaw());
        } catch (IllegalArgumentException e) {
            return Resultado.rechazo("Monto inválido");
        }
        if (montoCents <= 0) {
            return Resultado.rechazo("Monto inválido");
        }

        String origen = in.usuarioOrigen().trim().toUpperCase();
        String destino = in.usuarioDestino().trim().toUpperCase();

        if (origen.equals(destino)) {
            return Resultado.rechazo("No podés transferirte a vos mismo");
        }

        String tipoRed = destino.length() == 22 ? "E" : "I";

        String idCoelsa = "";
        String caeAfip = "";
        long comisionCents = 0;
        long impuestoCents = 0;

        if ("E".equals(tipoRed)) {
            try {
                idCoelsa = coelsa.simular(destino);
            } catch (RuntimeException e) {
                return Resultado.rechazo(e.getMessage());
            }
            comisionCents = 1500;
            impuestoCents = comisionCents * 21 / 100;
            caeAfip = arca.simular();
        }

        final long montoCentsF = montoCents;
        final long comisionCentsF = comisionCents;
        final long impuestoCentsF = impuestoCents;
        final String idCoelsaF = idCoelsa;
        final String caeAfipF = caeAfip;

        return tx.execute(status -> {
            String idOperacion;
            try {
                idOperacion = jdbc.queryForObject(
                        "INSERT INTO operacion (tipo_operacion, id_idempotencia, id_coelsa, cae_afip, estado) " +
                                "VALUES ('TRFR', ?::uuid, ?, ?, 'PENDIENTE') RETURNING id_operacion",
                        String.class, in.idIdempotencia(), idCoelsaF, caeAfipF);
            } catch (DuplicateKeyException e) {
                throw new IdempotenciaDuplicadaException();
            }

            String primero = origen.compareTo(destino) <= 0 ? origen : destino;
            String segundo = origen.compareTo(destino) <= 0 ? destino : origen;

            List<Map<String, Object>> filas = jdbc.queryForList(
                    "SELECT id_usuario, saldo_actual::text AS saldo, estado FROM usuarios " +
                            "WHERE id_usuario IN (?, ?) ORDER BY id_usuario FOR UPDATE",
                    primero, segundo);

            Map<String, Long> saldos = new HashMap<>();
            Map<String, Boolean> activos = new HashMap<>();
            for (Map<String, Object> fila : filas) {
                String id = ((String) fila.get("id_usuario")).trim();
                saldos.put(id, Money.parseAmountToCents((String) fila.get("saldo")));
                activos.put(id, "ACT".equals(((String) fila.get("estado")).trim()));
            }

            if (!saldos.containsKey(origen) || !activos.getOrDefault(origen, false)) {
                throw new NoEncontradoException();
            }

            long saldoOrigenCents = saldos.get(origen);
            long totalDebitoCents = montoCentsF + comisionCentsF + impuestoCentsF;

            if (saldoOrigenCents < totalDebitoCents) {
                return rechazarConLog(idOperacion, "FI", "Fondos insuficientes", saldoOrigenCents);
            }

            boolean destinoInterno = "I".equals(tipoRed);
            if (destinoInterno && (!saldos.containsKey(destino) || !activos.getOrDefault(destino, false))) {
                return rechazarConLog(idOperacion, "CD", "Cuenta destino inexistente o inactiva", saldoOrigenCents);
            }

            jdbc.update(
                    "INSERT INTO movimiento (id_operacion, id_usuario, tipo, monto, contraparte) " +
                            "VALUES (?::uuid, ?, 'TS', ?::numeric, ?)",
                    idOperacion, origen, Money.centsToDecimalString(-totalDebitoCents), destino);

            long nuevoSaldoOrigenCents = saldoOrigenCents - totalDebitoCents;
            jdbc.update("UPDATE usuarios SET saldo_actual = ?::numeric WHERE id_usuario = ?",
                    Money.centsToDecimalString(nuevoSaldoOrigenCents), origen);

            boolean usuarioActivo = false;
            if (destinoInterno) {
                jdbc.update(
                        "INSERT INTO movimiento (id_operacion, id_usuario, tipo, monto, contraparte) " +
                                "VALUES (?::uuid, ?, 'TR', ?::numeric, ?)",
                        idOperacion, destino, Money.centsToDecimalString(montoCentsF), origen);
                long nuevoSaldoDestinoCents = saldos.get(destino) + montoCentsF;
                jdbc.update("UPDATE usuarios SET saldo_actual = ?::numeric WHERE id_usuario = ?",
                        Money.centsToDecimalString(nuevoSaldoDestinoCents), destino);
                usuarioActivo = true;
            }

            jdbc.update("UPDATE operacion SET estado = 'COMPLETADA' WHERE id_operacion = ?::uuid", idOperacion);

            Desglose desglose = new Desglose(
                    Money.centsToDecimalString(montoCentsF),
                    Money.centsToDecimalString(comisionCentsF),
                    Money.centsToDecimalString(impuestoCentsF));
            Auditoria auditoria = new Auditoria(idOperacion, idCoelsaF, caeAfipF);
            String saldoRestante = Money.centsToDecimalString(nuevoSaldoOrigenCents);

            return new Resultado(true, "Transferencia realizada con éxito", saldoRestante, auditoria, desglose, usuarioActivo);
        });
    }

    private Resultado rechazarConLog(String idOperacion, String codigo, String mensaje, long saldoOrigenCents) {
        jdbc.update("UPDATE operacion SET estado = 'RECHAZADA' WHERE id_operacion = ?::uuid", idOperacion);
        jdbc.update(
                "INSERT INTO operacion_rechazo (id_operacion, codigo_error, mensaje_error, origen) " +
                        "VALUES (?::uuid, ?, ?, 'JAVA')",
                idOperacion, codigo, mensaje);
        return Resultado.rechazo(mensaje, Money.centsToDecimalString(saldoOrigenCents));
    }
}
