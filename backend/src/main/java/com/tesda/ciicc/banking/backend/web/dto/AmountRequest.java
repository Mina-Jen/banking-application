package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request body with a single amount")
public record AmountRequest(

        @Schema(description = "Amount as a decimal string: greater than zero, at most two decimal places",
                type = "string", example = "2000.00")
        String amount) {
}
