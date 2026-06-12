package com.wordpress.kkaravitis.banking.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MovieGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieGatewayApplication.class, args);
    }
}
