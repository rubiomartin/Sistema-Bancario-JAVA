package com.corebank.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationHub extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationHub.class);

    private final Map<String, Set<WebSocketSession>> salas = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String usuario = usuarioDeLaSesion(session);
        salas.computeIfAbsent(usuario, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String usuario = usuarioDeLaSesion(session);
        Set<WebSocketSession> sesiones = salas.get(usuario);
        if (sesiones != null) {
            sesiones.remove(session);
        }
    }

    public void notificar(String usuario, String evento, Object payload) {
        Set<WebSocketSession> sesiones = salas.get(usuario);
        if (sesiones == null || sesiones.isEmpty()) {
            return;
        }

        Map<String, Object> mensaje = new LinkedHashMap<>();
        mensaje.put("evento", evento);
        mensaje.put("payload", payload);

        TextMessage texto;
        try {
            texto = new TextMessage(objectMapper.writeValueAsString(mensaje));
        } catch (IOException e) {
            return;
        }

        for (WebSocketSession s : sesiones) {
            try {
                if (s.isOpen()) {
                    s.sendMessage(texto);
                }
            } catch (IOException e) {
                log.warn("[ws] no se pudo notificar a {}: {}", usuario, e.getMessage());
            }
        }
    }

    private String usuarioDeLaSesion(WebSocketSession session) {
        return (String) session.getAttributes().get(AuthHandshakeInterceptor.ATRIBUTO_USUARIO);
    }
}
