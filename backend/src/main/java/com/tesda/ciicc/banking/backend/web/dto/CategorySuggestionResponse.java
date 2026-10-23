package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A suggested category; nothing is stored until the caller accepts it")
public record CategorySuggestionResponse(

        @Schema(description = "Transaction the suggestion was made for", example = "42")
        int transactionId,

        @Schema(description = "Suggested category code", example = "DINING")
        String suggestedCategory) {
}
