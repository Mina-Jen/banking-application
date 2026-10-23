package com.tesda.ciicc.banking.backend.web.dto;

import com.tesda.ciicc.banking.backend.model.AnomalyFlag;
import com.tesda.ciicc.banking.backend.web.Money;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A rule finding about one transaction, waiting for or closed by an ADMIN review")
public record AnomalyFlagResponse(

        @Schema(description = "Flag id", example = "12")
        int id,

        @Schema(description = "The flagged transaction", example = "42")
        int transactionId,

        @Schema(description = "Reference of the flagged transaction", example = "TXN-lz3k9x00a1b2")
        String transactionReference,

        @Schema(description = "Account the transaction belongs to", example = "200001")
        String accountNumber,

        @Schema(description = "WITHDRAW or TRANSFER_OUT", example = "WITHDRAW")
        String transactionType,

        @Schema(description = "Flagged amount as a decimal string", type = "string", example = "5000.00")
        String amount,

        @Schema(description = "UNUSUAL_AMOUNT or VELOCITY", example = "UNUSUAL_AMOUNT")
        String rule,

        @Schema(description = "OPEN, REVIEWED or DISMISSED", example = "OPEN")
        String status,

        @Schema(description = "What the rule saw, in words", example = "5000.00 is at least 3x the 100.00 average of the last 3 outbound transactions (flag floor 1000.00).")
        String details,

        @Schema(description = "When the flag was raised, ISO-8601", example = "2026-10-22T10:15:30Z")
        String createdAt,

        @Schema(description = "When an admin closed the flag, absent while OPEN", example = "2026-10-22T11:00:00Z")
        String reviewedAt,

        @Schema(description = "Admin who closed the flag, absent while OPEN", example = "admin")
        String reviewedBy) {

    public static AnomalyFlagResponse from(AnomalyFlag flag) {
        return new AnomalyFlagResponse(
                flag.getId(),
                flag.getTransactionId(),
                flag.getTransactionReference(),
                flag.getAccountNumber(),
                flag.getTransactionType().name(),
                Money.format(flag.getAmount()),
                flag.getRule().name(),
                flag.getStatus().name(),
                flag.getDetails(),
                flag.getCreatedAt().toInstant().toString(),
                flag.getReviewedAt() == null ? null : flag.getReviewedAt().toInstant().toString(),
                flag.getReviewedBy());
    }
}
