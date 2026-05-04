package com.coube.delivery.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Coube Delivery API")
                        .description("""
                                Computes delivery prices from distance, weight, cargo type, and urgency.
                                Each calculation is persisted as an audit record in PostgreSQL.
                                """)
                        .version("v1"));
    }
}
