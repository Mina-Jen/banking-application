package com.tesda.ciicc.banking.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The result of comparing every account's stored balance with the sum of its
 * ledger rows. Money stays {@link BigDecimal} here; the two-decimal strings
 * belong to the web layer.
 */
public record ReconciliationReport(
        boolean reconciled,
        Instant checkedAt,
        int checkedAccounts,
        BigDecimal ledgerTotal,
        BigDecimal storedTotal,
        List<Discrepancy> discrepancies,
        boolean discrepanciesTruncated) {

    /** One account whose ledger does not add up to its stored balance. */
    public record Discrepancy(
            String accountNumber,
            String accountName,
            BigDecimal ledgerBalance,
            BigDecimal storedBalance,
            BigDecimal difference) {
    }
}
