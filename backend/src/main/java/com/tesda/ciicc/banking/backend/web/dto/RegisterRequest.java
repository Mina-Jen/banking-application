package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for registering a login")
public record RegisterRequest(

        @Schema(description = "3 to 50 characters: lowercase letters, digits, dot, underscore, hyphen",
                example = "juan.dela")
        String username,

        @Schema(description = "8 to 72 characters", example = "correct-horse-battery")
        String password) {
}
