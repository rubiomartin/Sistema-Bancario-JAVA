package com.corebank.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DataSourceConfig {

    @Value("${DATABASE_URL:postgres://corebank:corebank@localhost:5432/corebank?sslmode=disable}")
    private String databaseUrl;

    @Bean
    public DataSource dataSource() {
        ParsedUrl parsed = parse(databaseUrl);

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(parsed.jdbcUrl());
        ds.setUsername(parsed.usuario());
        ds.setPassword(parsed.clave());
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    record ParsedUrl(String jdbcUrl, String usuario, String clave) {
    }

    static ParsedUrl parse(String databaseUrl) {
        try {
            URI uri = new URI(databaseUrl);
            String[] credenciales = uri.getUserInfo() != null ? uri.getUserInfo().split(":", 2) : new String[]{"", ""};
            String usuario = credenciales.length > 0 ? credenciales[0] : "";
            String clave = credenciales.length > 1 ? credenciales[1] : "";

            String query = uri.getQuery() != null ? "?" + uri.getQuery() : "";
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort() + uri.getPath() + query;

            return new ParsedUrl(jdbcUrl, usuario, clave);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("DATABASE_URL inválida: " + databaseUrl, e);
        }
    }
}
