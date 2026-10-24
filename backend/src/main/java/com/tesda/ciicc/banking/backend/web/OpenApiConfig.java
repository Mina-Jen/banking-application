package com.tesda.ciicc.banking.backend.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Names the API in Swagger UI and in the generated OpenAPI document. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sinagOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Sinag API")
                .version("1.0.0")
                .description("Sinag — bukas na abot-kamay. REST API for accounts, transfers, "
                        + "spending categories and the operations review queue. "
                        + "Demo project. Not a licensed bank."));
    }
}
