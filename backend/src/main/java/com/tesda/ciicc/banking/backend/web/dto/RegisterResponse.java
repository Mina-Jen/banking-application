package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Confirmation of a registered login")
public record RegisterResponse(

        @Schema(example = "juan.dela")
        String username,

        @Schema(description = "Registrations always create this role", example = "CUSTOMER")
        String role) {
}
