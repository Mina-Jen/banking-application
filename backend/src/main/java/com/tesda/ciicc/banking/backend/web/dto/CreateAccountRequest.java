package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for creating an account")
public record CreateAccountRequest(

        @Schema(description = "Unique account number, at most 20 characters", example = "100001")
        String accountNumber,

        @Schema(description = "Account holder name, at most 100 characters", example = "Juan Dela Cruz")
        String accountName,

        @Schema(description = "Opening deposit as a decimal string; zero creates the account without a transaction",
                type = "string", example = "5000.00")
        String initialDeposit) {
}
