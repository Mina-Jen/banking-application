package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body for a transfer between two accounts")
public record TransferRequest(

        @Schema(description = "Account to take the money from", example = "100001")
        String sourceAccountNumber,

        @Schema(description = "Account to put the money into", example = "100002")
        String destinationAccountNumber,

        @Schema(description = "Amount as a decimal string: greater than zero, at most two decimal places",
                type = "string", example = "2000.00")
        String amount) {
}
