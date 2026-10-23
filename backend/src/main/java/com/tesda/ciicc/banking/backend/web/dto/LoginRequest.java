package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for logging in")
public record LoginRequest(

        @Schema(example = "juan.dela")
        String username,

        @Schema(example = "correct-horse-battery")
        String password) {
}
