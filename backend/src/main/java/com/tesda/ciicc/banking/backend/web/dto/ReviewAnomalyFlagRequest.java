package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "An admin's decision about an anomaly flag")
public record ReviewAnomalyFlagRequest(

        @Schema(description = "REVIEWED (handled) or DISMISSED (nothing wrong)", example = "REVIEWED")
        String status) {
}
