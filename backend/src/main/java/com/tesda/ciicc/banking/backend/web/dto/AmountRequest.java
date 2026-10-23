package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body with an amount and an optional note")
public record AmountRequest(

        @Schema(description = "Amount as a decimal string: greater than zero, at most two decimal places",
                type = "string", example = "2000.00")
        String amount,

        @Schema(description = "Optional note stored with the transaction, at most 255 characters",
                example = "SM supermarket")
        String description) {

    public AmountRequest(String amount) {
        this(amount, null);
    }
}
