package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "One read-only tool call the assistant executed for this answer")
public record AssistantToolCallResponse(

        @Schema(example = "get_balance")
        String tool,

        @Schema(description = "Arguments after unknown model-supplied fields were removed")
        Map<String, Object> arguments) {
}
