package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "The assistant's answer and the read-only lookups behind it")
public record AssistantChatResponse(

        String reply,

        @Schema(description = "Tool calls executed for this answer, in order")
        List<AssistantToolCallResponse> toolCalls,

        @Schema(description = "Transfers proposed but NOT executed; empty when none were proposed")
        List<TransferProposalResponse> transferProposals) {
}
