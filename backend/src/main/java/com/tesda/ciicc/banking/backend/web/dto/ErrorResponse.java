package com.tesda.ciicc.banking.backend.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;

@Schema(description = "Error body returned by every handled API error")
public record ErrorResponse(

        @Schema(description = "HTTP status code", example = "422")
        int status,

        @Schema(description = "HTTP reason phrase", example = "Unprocessable Entity")
        String error,

        @Schema(description = "Human-readable explanation", example = "Deposit amount must be greater than zero.")
        String message) {

    public static ErrorResponse of(HttpStatus status, String message) {
        return new ErrorResponse(status.value(), status.getReasonPhrase(), message);
    }
}
