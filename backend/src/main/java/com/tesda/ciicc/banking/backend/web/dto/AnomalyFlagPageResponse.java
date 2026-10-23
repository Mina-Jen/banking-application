package com.tesda.ciicc.banking.backend.web.dto;

import com.tesda.ciicc.banking.backend.model.AnomalyFlag;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "One page of anomaly flags, newest first")
public record AnomalyFlagPageResponse(

        @Schema(description = "Flags of this page")
        List<AnomalyFlagResponse> flags,

        @Schema(description = "Zero-based page number", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Total number of flags matching the filter", example = "5")
        long totalElements,

        @Schema(description = "Total number of pages", example = "1")
        int totalPages) {

    public static AnomalyFlagPageResponse from(Page<AnomalyFlag> result) {
        return new AnomalyFlagPageResponse(
                result.getContent().stream().map(AnomalyFlagResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
