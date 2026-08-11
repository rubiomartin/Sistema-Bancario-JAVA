package com.corebank.domain;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    public AuthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ResultadoLogin(boolean ok, String mensaje) {
    }

    public ResultadoLogin login(String idUsuario, String password) {
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT password_hash, estado FROM usuarios WHERE id_usuario = ?", idUsuario);

        if (filas.isEmpty()) {
            return new ResultadoLogin(false, "Usuario o contraseña inválidos");
        }

        String hash = (String) filas.get(0).get("password_hash");
        String estado = ((String) filas.get(0).get("estado")).trim();

        if (!"ACT".equals(estado)) {
            return new ResultadoLogin(false, "La cuenta no está activa");
        }
        if (!bcrypt.matches(password, hash)) {
            return new ResultadoLogin(false, "Usuario o contraseña inválidos");
        }
        return new ResultadoLogin(true, "Bienvenido/a de nuevo");
    }

    public String consultarSaldo(String idUsuario) {
        try {
            return jdbc.queryForObject(
                    "SELECT saldo_actual::text FROM usuarios WHERE id_usuario = ?", String.class, idUsuario);
        } catch (EmptyResultDataAccessException e) {
            throw new NoEncontradoException();
        }
    }
}
