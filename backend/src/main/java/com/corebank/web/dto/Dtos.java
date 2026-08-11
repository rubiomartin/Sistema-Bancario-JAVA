package com.corebank.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public final class Dtos {
    private Dtos() {
    }

    public record LoginRequest(String usuario, String password) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LoginResponse(String status, String message, String token) {
    }

    public record TransaccionRequest(String operacion, Object monto) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransaccionResponse(String status, String message, String saldo) {
    }

    public record TransferenciaRequest(String usuarioDestino, Object monto) {
    }

    public record AuditoriaDTO(
            @JsonProperty("id_operacion") String idOperacion,
            @JsonProperty("id_coelsa") String idCoelsa,
            @JsonProperty("factura_afip") String facturaAfip) {
    }

    public record DesgloseDTO(
            @JsonProperty("neto_transferido") String netoTransferido,
            @JsonProperty("comision_fintech") String comisionFintech,
            @JsonProperty("iva_recaudado") String ivaRecaudado) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TransferenciaResponse(
            String status, String message,
            @JsonProperty("saldo_restante") String saldoRestante,
            AuditoriaDTO auditoria, DesgloseDTO desglose) {
    }

    public record HistorialRequest(String fechaDesde, String fechaHasta, Integer pagina) {
    }

    public record MovimientoDTO(String fecha, String tipo, String monto, String contraparte) {
    }

    public record HistorialResponse(String status, List<MovimientoDTO> movimientos) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String error, String status, String message) {
        public static ErrorResponse of(String error) {
            return new ErrorResponse(error, null, null);
        }

        public static ErrorResponse status(String status, String message) {
            return new ErrorResponse(null, status, message);
        }
    }

    public static String montoComoTexto(Object v) {
        if (v instanceof String s) {
            return s;
        }
        if (v instanceof Number n) {
            double d = n.doubleValue();
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.valueOf(d);
        }
        return "0";
    }
}
