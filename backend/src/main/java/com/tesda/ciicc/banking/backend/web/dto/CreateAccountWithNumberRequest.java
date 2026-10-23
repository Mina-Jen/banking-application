package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ADMIN only. Operators key accounts that were opened on paper or in another
 * system, so this is the one place where a chosen number is accepted — and the
 * one place where "that number already exists" (409) may be answered.
 */
@Schema(description = "Request body for an ADMIN opening an account with a chosen number")
public record CreateAccountWithNumberRequest(

        @Schema(description = "Account number chosen by the operator, at most 20 characters",
                example = "100001")
        String accountNumber,

        @Schema(description = "Account holder name, at most 100 characters", example = "Juan Dela Cruz")
        String accountName,

        @Schema(description = "Opening deposit as a decimal string; zero creates the account without a transaction",
                type = "string", example = "5000.00")
        String initialDeposit) {
}
