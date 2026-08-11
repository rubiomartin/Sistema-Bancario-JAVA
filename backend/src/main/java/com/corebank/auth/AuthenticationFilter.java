package com.corebank.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public class AuthenticationFilter extends OncePerRequestFilter {

    public static final String ATRIBUTO_USUARIO = "usuarioAutenticado";

    private final TokenService tokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String token = extraerToken(request);
        Optional<String> usuario = tokenService.validar(token);

        if (usuario.isEmpty()) {
            String mensaje = (token == null || token.isBlank())
                    ? "Falta autenticación."
                    : "Sesión inválida o expirada. Volvé a iniciar sesión.";
            responderNoAutorizado(response, mensaje);
            return;
        }

        request.setAttribute(ATRIBUTO_USUARIO, usuario.get());
        chain.doFilter(request, response);
    }

    private String extraerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring("Bearer ".length());
        }

        return request.getParameter("token");
    }

    private void responderNoAutorizado(HttpServletResponse response, String mensaje) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, String> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", "ERROR");
        cuerpo.put("message", mensaje);
        objectMapper.writeValue(response.getWriter(), cuerpo);
    }
}
