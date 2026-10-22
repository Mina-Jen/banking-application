package com.tesda.ciicc.banking.backend.web.dto;

import com.tesda.ciicc.banking.backend.model.Transaction;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "One page of an account's transaction history, oldest first")
public record TransactionPageResponse(

        @Schema(description = "Transactions of this page")
        List<TransactionResponse> transactions,

        @Schema(description = "Zero-based page number", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Total number of transactions for the account", example = "42")
        long totalElements,

        @Schema(description = "Total number of pages", example = "3")
        int totalPages) {

    public static TransactionPageResponse from(Page<Transaction> result) {
        return new TransactionPageResponse(
                result.getContent().stream().map(TransactionResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
