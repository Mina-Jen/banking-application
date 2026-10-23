package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One chat message for the banking assistant")
public record AssistantChatRequest(

        @Schema(description = "The customer's question, 1 to 2000 characters",
                example = "How much did I spend on groceries this month?")
        String message) {
}
