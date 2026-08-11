package com.corebank.web;

import com.corebank.auth.AuthenticationFilter;
import com.corebank.domain.AuthService;
import com.corebank.domain.CajaService;
import com.corebank.domain.HistorialService;
import com.corebank.domain.NoEncontradoException;
import com.corebank.domain.TransferenciaService;
import com.corebank.web.dto.Dtos;
import com.corebank.web.dto.Dtos.*;
import com.corebank.ws.NotificationHub;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
public class BancoController {

    private static final Logger log = LoggerFactory.getLogger(BancoController.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ISO_LOCAL_DATE;

    private final AuthService authService;
    private final CajaService cajaService;
    private final TransferenciaService transferenciaService;
    private final HistorialService historialService;
    private final NotificationHub hub;

    public BancoController(AuthService authService, CajaService cajaService, TransferenciaService transferenciaService,
                            HistorialService historialService, NotificationHub hub) {
        this.authService = authService;
        this.cajaService = cajaService;
        this.transferenciaService = transferenciaService;
        this.historialService = historialService;
        this.hub = hub;
    }

    @PostMapping("/api/transaccion")
    public ResponseEntity<?> transaccion(@RequestBody(required = false) TransaccionRequest req, HttpServletRequest request) {
        String idUsuario = usuarioAutenticado(request);

        if (req == null || req.operacion() == null || req.operacion().isBlank()) {
            return ResponseEntity.badRequest().body(ErrorResponse.of("Datos insuficientes"));
        }

        String operacion = req.operacion();
        String monto = "C".equals(operacion) ? "0" : montoComoTexto(req.monto());

        if ("C".equals(operacion)) {
            try {
                String saldo = authService.consultarSaldo(idUsuario);
                return ResponseEntity.ok(new TransaccionResponse("SUCCESS", null, saldo));
            } catch (NoEncontradoException e) {
                return ResponseEntity.badRequest().body(new TransaccionResponse("ERROR", "Usuario no encontrado", null));
            } catch (RuntimeException e) {
                log.error("[transaccion] error", e);
                return ResponseEntity.internalServerError().body(ErrorResponse.of(e.getMessage()));
            }
        }

        CajaService.ResultadoCaja resultado;
        try {
            resultado = cajaService.registrarCaja(idUsuario, operacion, monto);
        } catch (RuntimeException e) {
            log.error("[transaccion] error", e);
            return ResponseEntity.internalServerError().body(ErrorResponse.of(e.getMessage()));
        }

        if (!resultado.ok()) {
            return ResponseEntity.badRequest().body(new TransaccionResponse("ERROR", resultado.mensaje(), null));
        }
        return ResponseEntity.ok(new TransaccionResponse("SUCCESS", resultado.mensaje(), resultado.saldo()));
    }

    @PostMapping("/api/transferencia")
    public ResponseEntity<?> transferencia(@RequestBody(required = false) TransferenciaRequest req,
                                            @RequestHeader(value = "x-idempotency-key", required = false) String idIdempotencia,
                                            HttpServletRequest request) {
        String idUsuario = usuarioAutenticado(request);

        if (idIdempotencia == null || idIdempotencia.isBlank()) {
            return ResponseEntity.badRequest().body(ErrorResponse.status("ERROR", "Falta la llave de idempotencia."));
        }
        if (req == null || req.usuarioDestino() == null || req.usuarioDestino().isBlank() || req.monto() == null) {
            return ResponseEntity.badRequest().body(ErrorResponse.of("Parámetros incompletos"));
        }

        TransferenciaService.Entrada entrada = new TransferenciaService.Entrada(
                idUsuario, req.usuarioDestino(), montoComoTexto(req.monto()), idIdempotencia);

        TransferenciaService.Resultado resultado;
        try {
            resultado = transferenciaService.registrarTransferencia(entrada);
        } catch (com.corebank.domain.IdempotenciaDuplicadaException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.status(
                    "ERROR", "Esta operación ya fue recibida y está en proceso. Se bloqueó la duplicidad."));
        } catch (NoEncontradoException e) {
            return ResponseEntity.badRequest().body(new TransferenciaResponse(
                    "RECHAZADA", "Usuario origen no encontrado o inactivo", null, null, null));
        } catch (RuntimeException e) {
            log.error("[transferencia] error", e);
            return ResponseEntity.internalServerError().body(ErrorResponse.of(e.getMessage()));
        }

        if (!resultado.ok()) {
            return ResponseEntity.badRequest().body(new TransferenciaResponse(
                    "RECHAZADA", resultado.mensaje(), null, null, null));
        }

        if (resultado.usuarioActivo()) {
            String destino = req.usuarioDestino().trim().toUpperCase();
            hub.notificar(destino, "notificacion_transferencia", new java.util.LinkedHashMap<>() {{
                put("titulo", "¡Dinero recibido!");
                put("monto", resultado.desglose().netoTransferido());
                put("origen", idUsuario);
            }});
        }

        return ResponseEntity.ok(new TransferenciaResponse(
                "SUCCESS", resultado.mensaje(), resultado.saldoRestante(),
                new AuditoriaDTO(
                        resultado.auditoria().idOperacion(),
                        orDefault(resultado.auditoria().idCoelsa(), "Interna P2P"),
                        orDefault(resultado.auditoria().caeAfip(), "Exenta")),
                new DesgloseDTO(
                        resultado.desglose().netoTransferido(),
                        resultado.desglose().comisionFintech(),
                        resultado.desglose().ivaRecaudado())));
    }

    @PostMapping("/api/historial")
    public ResponseEntity<?> historial(@RequestBody(required = false) HistorialRequest req, HttpServletRequest request) {
        String idUsuario = usuarioAutenticado(request);

        String fechaDesde = req != null ? req.fechaDesde() : null;
        String fechaHasta = req != null ? req.fechaHasta() : null;
        int pagina = req != null && req.pagina() != null ? req.pagina() : 1;

        List<HistorialService.Movimiento> movs;
        try {
            movs = historialService.historial(idUsuario, fechaDesde, fechaHasta, pagina);
        } catch (RuntimeException e) {
            log.error("[historial] error", e);
            return ResponseEntity.internalServerError().body(ErrorResponse.of(e.getMessage()));
        }

        List<MovimientoDTO> dtos = movs.stream()
                .map(m -> new MovimientoDTO(m.fecha().format(FECHA), m.tipo(), m.monto(), m.contraparte()))
                .toList();

        return ResponseEntity.ok(new HistorialResponse("SUCCESS", dtos));
    }

    private String usuarioAutenticado(HttpServletRequest request) {
        return (String) request.getAttribute(AuthenticationFilter.ATRIBUTO_USUARIO);
    }

    private String montoComoTexto(Object monto) {
        return Dtos.montoComoTexto(monto);
    }

    private String orDefault(String v, String fallback) {
        return v == null || v.isBlank() ? fallback : v;
    }
}
