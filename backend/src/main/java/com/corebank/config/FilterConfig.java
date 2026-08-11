package com.corebank.config;

import com.corebank.auth.AuthenticationFilter;
import com.corebank.auth.TokenService;
import com.corebank.web.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> limiteGeneral() {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(new RateLimitFilter(10.0, 20.0));
        reg.addUrlPatterns("/*");
        reg.setOrder(1);
        reg.setName("limiteGeneral");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> limiteLogin() {
        FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(new RateLimitFilter(5.0 / 60.0, 3.0));
        reg.addUrlPatterns("/api/login");
        reg.setOrder(2);
        reg.setName("limiteLogin");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<AuthenticationFilter> autenticacion(TokenService tokenService) {
        FilterRegistrationBean<AuthenticationFilter> reg =
                new FilterRegistrationBean<>(new AuthenticationFilter(tokenService));
        reg.addUrlPatterns("/api/transaccion", "/api/transferencia", "/api/historial");
        reg.setOrder(3);
        reg.setName("autenticacion");
        return reg;
    }
}
