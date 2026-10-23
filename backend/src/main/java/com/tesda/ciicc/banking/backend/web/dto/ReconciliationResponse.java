package com.tesda.ciicc.banking.backend.web.dto;

import com.tesda.ciicc.banking.backend.service.ReconciliationReport;
import com.tesda.ciicc.banking.backend.web.Money;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Result of comparing every account's stored balance with the sum of its ledger rows")
public record ReconciliationResponse(

        @Schema(description = "True when every account's balance equals its ledger", example = "false")
        boolean reconciled,

        @Schema(description = "When the check ran, ISO-8601", example = "2026-10-22T10:15:30Z")
        String checkedAt,

        @Schema(description = "How many accounts were compared", example = "12")
        int checkedAccounts,

        @Schema(description = "Sum of the ledger over those accounts, as a decimal string", type = "string", example = "1050.00")
        String ledgerTotal,

        @Schema(description = "Sum of the stored balances, as a decimal string", type = "string", example = "1045.00")
        String storedTotal,

        @Schema(description = "Accounts that do not add up, by account number")
        List<DiscrepancyResponse> discrepancies,

        @Schema(description = "True when more accounts mismatched than could be listed here")
        boolean discrepanciesTruncated) {

    @Schema(description = "One account whose ledger does not match its stored balance")
    public record DiscrepancyResponse(

            @Schema(description = "Account number", example = "200001")
            String accountNumber,

            @Schema(description = "Account holder name", example = "Alice Santos")
            String accountName,

            @Schema(description = "Balance the ledger implies, as a decimal string", type = "string", example = "1050.00")
            String ledgerBalance,

            @Schema(description = "Balance stored on the account, as a decimal string", type = "string", example = "1045.00")
            String storedBalance,

            @Schema(description = "Ledger minus stored balance: positive means the ledger holds more than the account",
                    type = "string", example = "5.00")
            String difference) {

        static DiscrepancyResponse from(ReconciliationReport.Discrepancy discrepancy) {
            return new DiscrepancyResponse(
                    discrepancy.accountNumber(),
                    discrepancy.accountName(),
                    Money.format(discrepancy.ledgerBalance()),
                    Money.format(discrepancy.storedBalance()),
                    Money.format(discrepancy.difference()));
        }
    }

    public static ReconciliationResponse from(ReconciliationReport report) {
        return new ReconciliationResponse(
                report.reconciled(),
                report.checkedAt().toString(),
                report.checkedAccounts(),
                Money.format(report.ledgerTotal()),
                Money.format(report.storedTotal()),
                report.discrepancies().stream().map(DiscrepancyResponse::from).toList(),
                report.discrepanciesTruncated());
    }
}
