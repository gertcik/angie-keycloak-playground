package com.bank.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Bank API")
                        .description("REST API for bank operations")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Bank API Support")
                                .email("support@bank.com")));
    }
}