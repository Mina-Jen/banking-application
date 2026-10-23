package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A transfer the assistant suggested but did not execute")
public record TransferProposalResponse(

        @Schema(example = "200001")
        String sourceAccountNumber,

        @Schema(example = "200002")
        String destinationAccountNumber,

        @Schema(type = "string", example = "100.00")
        String amount,

        @Schema(example = "PROPOSED")
        String status,

        String note) {
}
