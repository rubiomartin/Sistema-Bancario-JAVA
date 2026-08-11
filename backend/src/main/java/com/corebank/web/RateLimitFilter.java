package com.corebank.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, TokenBucket> limitadores = new ConcurrentHashMap<>();
    private final double tokensPorSegundo;
    private final double burst;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RateLimitFilter(double tokensPorSegundo, double burst) {
        this.tokensPorSegundo = tokensPorSegundo;
        this.burst = burst;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String ip = clientIp(request);
        TokenBucket limitador = limitadores.computeIfAbsent(ip, k -> new TokenBucket(tokensPorSegundo, burst));

        if (!limitador.tryAcquire()) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            Map<String, String> cuerpo = new LinkedHashMap<>();
            cuerpo.put("status", "ERROR");
            cuerpo.put("message", "Demasiadas solicitudes. Esperá un momento e intentá de nuevo.");
            objectMapper.writeValue(response.getWriter(), cuerpo);
            return;
        }

        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        return ip != null ? ip : "desconocido";
    }
}
