package com.corebank.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

@Service
public class CajaService {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public CajaService(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
    }

    public record ResultadoCaja(boolean ok, String mensaje, String saldo) {
    }

    public ResultadoCaja registrarCaja(String idUsuario, String operacion, String montoRaw) {
        long montoCents;
        try {
            montoCents = Money.parseAmountToCents(montoRaw);
        } catch (IllegalArgumentException e) {
            return new ResultadoCaja(false, "Monto inválido", null);
        }
        if (montoCents <= 0) {
            return new ResultadoCaja(false, "Monto inválido", null);
        }
        final long montoCentsF = montoCents;

        return tx.execute(status -> {
            List<Map<String, Object>> filas = jdbc.queryForList(
                    "SELECT saldo_actual::text AS saldo, estado FROM usuarios WHERE id_usuario = ? FOR UPDATE",
                    idUsuario);

            if (filas.isEmpty()) {
                return new ResultadoCaja(false, "Usuario no encontrado", null);
            }

            String saldoActual = (String) filas.get(0).get("saldo");
            String estado = ((String) filas.get(0).get("estado")).trim();
            if (!"ACT".equals(estado)) {
                return new ResultadoCaja(false, "La cuenta no está activa", saldoActual);
            }

            long saldoCents = Money.parseAmountToCents(saldoActual);

            String tipoOp = "R".equals(operacion) ? "RETI" : "DEPO";
            String idOperacion = jdbc.queryForObject(
                    "INSERT INTO operacion (tipo_operacion, id_idempotencia, estado) " +
                            "VALUES (?, gen_random_uuid(), 'PENDIENTE') RETURNING id_operacion",
                    String.class, tipoOp);

            if ("R".equals(operacion) && saldoCents < montoCentsF) {
                jdbc.update("UPDATE operacion SET estado = 'RECHAZADA' WHERE id_operacion = ?::uuid", idOperacion);
                jdbc.update(
                        "INSERT INTO operacion_rechazo (id_operacion, codigo_error, mensaje_error, origen) " +
                                "VALUES (?::uuid, 'FI', 'FONDOS INSUFICIENTES', 'JAVA')",
                        idOperacion);
                return new ResultadoCaja(false, "Fondos insuficientes", saldoActual);
            }

            long movMontoCents = "R".equals(operacion) ? -montoCentsF : montoCentsF;
            String tipoMov = "R".equals(operacion) ? "R" : "D";

            jdbc.update(
                    "INSERT INTO movimiento (id_operacion, id_usuario, tipo, monto) VALUES (?::uuid, ?, ?, ?::numeric)",
                    idOperacion, idUsuario, tipoMov, Money.centsToDecimalString(movMontoCents));

            long nuevoSaldoCents = saldoCents + movMontoCents;
            String nuevoSaldo = Money.centsToDecimalString(nuevoSaldoCents);

            jdbc.update("UPDATE usuarios SET saldo_actual = ?::numeric WHERE id_usuario = ?", nuevoSaldo, idUsuario);
            jdbc.update("UPDATE operacion SET estado = 'COMPLETADA' WHERE id_operacion = ?::uuid", idOperacion);

            String mensaje = "R".equals(operacion) ? "Retiro realizado" : "Depósito acreditado";
            return new ResultadoCaja(true, mensaje, nuevoSaldo);
        });
    }
}
