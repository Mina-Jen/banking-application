package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The category the account owner accepted for a transaction")
public record SetCategoryRequest(

        @Schema(description = "One code from the category vocabulary", example = "GROCERIES")
        String category) {
}
