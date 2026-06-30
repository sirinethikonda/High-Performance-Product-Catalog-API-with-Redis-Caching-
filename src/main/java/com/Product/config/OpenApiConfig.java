package com.Product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("High-Performance Product Catalog API")
                        .version("1.0.0")
                        .description("RESTful Product Catalog API with Redis Caching and Rate Limiting."));
    }
}
