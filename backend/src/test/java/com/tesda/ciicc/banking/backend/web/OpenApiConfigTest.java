package com.tesda.ciicc.banking.backend.web;

import io.swagger.v3.oas.models.info.Info;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    @Test
    void namesTheProductInTheApiDocument() {
        Info info = new OpenApiConfig().sinagOpenApi().getInfo();

        assertEquals("Sinag API", info.getTitle());
        assertEquals("1.0.0", info.getVersion());
        assertTrue(info.getDescription().contains("bukas na abot-kamay"));
        assertTrue(info.getDescription().contains("Not a licensed bank."));
    }
}
