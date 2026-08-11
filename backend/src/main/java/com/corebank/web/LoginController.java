package com.corebank.web;

import com.corebank.auth.TokenService;
import com.corebank.domain.AuthService;
import com.corebank.web.dto.Dtos.ErrorResponse;
import com.corebank.web.dto.Dtos.LoginRequest;
import com.corebank.web.dto.Dtos.LoginResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController {

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final AuthService authService;
    private final TokenService tokenService;

    public LoginController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/api/login")
    public ResponseEntity<?> login(@RequestBody(required = false) LoginRequest req) {
        if (req == null || isBlank(req.usuario()) || isBlank(req.password())) {
            return ResponseEntity.badRequest().body(ErrorResponse.of("Usuario y contraseña requeridos"));
        }

        String idUsuario = req.usuario().trim().toUpperCase();
        AuthService.ResultadoLogin resultado;
        try {
            resultado = authService.login(idUsuario, req.password());
        } catch (RuntimeException e) {
            log.error("[login] error", e);
            return ResponseEntity.internalServerError().body(ErrorResponse.of(e.getMessage()));
        }

        if (!resultado.ok()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse("UNAUTHORIZED", resultado.mensaje(), null));
        }

        String token;
        try {
            token = tokenService.generar(idUsuario);
        } catch (RuntimeException e) {
            log.error("[login] no se pudo generar token", e);
            return ResponseEntity.internalServerError().body(ErrorResponse.of("no se pudo iniciar sesión"));
        }

        return ResponseEntity.ok(new LoginResponse("SUCCESS", resultado.mensaje(), token));
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
