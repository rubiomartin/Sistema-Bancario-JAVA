package com.corebank.config;

import com.corebank.auth.TokenService;
import com.corebank.ws.AuthHandshakeInterceptor;
import com.corebank.ws.NotificationHub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotificationHub hub;
    private final TokenService tokenService;

    @Value("${WS_ALLOWED_ORIGIN:*}")
    private String allowedOrigins;

    public WebSocketConfig(NotificationHub hub, TokenService tokenService) {
        this.hub = hub;
        this.tokenService = tokenService;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(hub, "/ws")
                .addInterceptors(new AuthHandshakeInterceptor(tokenService))
                .setAllowedOrigins(allowedOrigins.split(","));
    }
}
