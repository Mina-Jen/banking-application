package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Optional detail that helps the categorizer; the transaction itself is found by id")
public record CategorySuggestionRequest(

        @Schema(description = "Free-text note such as a merchant or purpose (at most 500 characters are used)",
                example = "Jollibee lunch with the team", nullable = true)
        String description) {
}
