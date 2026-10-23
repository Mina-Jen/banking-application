package com.tesda.ciicc.banking.backend.web.dto;

import com.tesda.ciicc.banking.backend.model.Account;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

@Schema(description = "One page of the accounts the caller may see, ordered by account number")
public record AccountPageResponse(

        @Schema(description = "Accounts of this page")
        List<AccountResponse> accounts,

        @Schema(description = "Zero-based page number", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Total number of accounts for this caller", example = "3")
        long totalElements,

        @Schema(description = "Total number of pages", example = "1")
        int totalPages) {

    public static AccountPageResponse from(Page<Account> result) {
        return new AccountPageResponse(
                result.getContent().stream().map(AccountResponse::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }
}
