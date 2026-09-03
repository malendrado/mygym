package com.cocha.mygym.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI mygymOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MyGym API")
                        .description("Backend REST de MyGym")
                        .version("0.0.1"));
    }
}
