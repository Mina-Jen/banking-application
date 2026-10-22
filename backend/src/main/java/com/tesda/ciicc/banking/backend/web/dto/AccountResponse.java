package com.tesda.ciicc.banking.backend.web.dto;

import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.web.Money;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A bank account")
public record AccountResponse(

        @Schema(example = "100001")
        String accountNumber,

        @Schema(example = "Juan Dela Cruz")
        String accountName,

        @Schema(description = "Balance in pesos with two decimal places", type = "string", example = "7000.00")
        String balance,

        @Schema(description = "When the account was created, ISO-8601", example = "2026-10-21T17:00:00Z")
        String createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getAccountNumber(),
                account.getAccountName(),
                Money.format(account.getBalance()),
                account.getCreatedAt().toInstant().toString());
    }
}
