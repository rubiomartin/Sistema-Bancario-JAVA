package com.corebank.domain;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class HistorialService {

    private final JdbcTemplate jdbc;

    public HistorialService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Movimiento(LocalDate fecha, String tipo, String monto, String contraparte) {
    }

    public List<Movimiento> historial(String idUsuario, String fechaDesde, String fechaHasta, int pagina) {
        String desde = (fechaDesde == null || fechaDesde.isBlank()) ? "0001-01-01" : fechaDesde;
        String hasta = (fechaHasta == null || fechaHasta.isBlank()) ? "9999-12-31" : fechaHasta;
        int pag = Math.max(pagina, 1);
        int offset = (pag - 1) * 10;

        return jdbc.query(
                "SELECT fecha_contable, tipo, monto::text AS monto, contraparte " +
                        "FROM movimiento " +
                        "WHERE id_usuario = ? AND fecha_contable BETWEEN ?::date AND ?::date " +
                        "ORDER BY creado_en DESC LIMIT 10 OFFSET ?",
                (rs, rowNum) -> new Movimiento(
                        rs.getDate("fecha_contable").toLocalDate(),
                        rs.getString("tipo"),
                        rs.getString("monto"),
                        rs.getString("contraparte")
                ),
                idUsuario, desde, hasta, offset
        );
    }
}
