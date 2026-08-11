package com.corebank;

import com.corebank.config.DotEnv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CorebankApplication {
    public static void main(String[] args) {
        DotEnv.load(".env");
        SpringApplication.run(CorebankApplication.class, args);
    }
}
