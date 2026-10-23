package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.repository.AccountRepository;
import com.tesda.ciicc.banking.backend.repository.AccountRepository.StoredBalance;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository.AccountTypeTotal;
import com.tesda.ciicc.banking.backend.service.ReconciliationReport.Discrepancy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reconciliation — the money-integrity check a bank lives by. Every account's
 * stored balance must equal the signed sum of its own ledger rows: deposits and
 * incoming transfers in, withdrawals and outgoing transfers out. Anything else
 * means money was created or destroyed, so this is how such a bug is found
 * rather than guessed at after a customer complains.
 *
 * <p>The check only reads. Correcting a balance is never automatic: the
 * discrepancy has to be explained first, and silently overwriting a balance
 * would hide the very bug this reports.</p>
 */
@Service
public class ReconciliationService {

    /** Beyond this many mismatching accounts the rest are counted, not listed. */
    private static final int MAX_DISCREPANCIES_LISTED = 100;

    private static final List<TransactionType> CREDIT_TYPES =
            List.of(TransactionType.DEPOSIT, TransactionType.TRANSFER_IN);
    private static final List<TransactionType> DEBIT_TYPES =
            List.of(TransactionType.WITHDRAW, TransactionType.TRANSFER_OUT);

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final Clock clock;

    public ReconciliationService(AccountRepository accounts, TransactionRepository transactions, Clock clock) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * ADMIN only. Three grouped queries whatever the size of the tables, so the
     * cost grows with the data rather than with the number of accounts.
     */
    @Transactional(readOnly = true)
    public ReconciliationReport reconcile(CurrentUser caller) {
        requireAdmin(caller);

        Map<String, BigDecimal> ledgerTotals = ledgerTotals();
        List<StoredBalance> storedAccounts = accounts.findAllStoredBalances();

        List<Discrepancy> discrepancies = new ArrayList<>();
        BigDecimal ledgerTotal = BigDecimal.ZERO;
        BigDecimal storedTotal = BigDecimal.ZERO;
        for (StoredBalance account : storedAccounts) {
            BigDecimal ledgerBalance = ledgerTotals.getOrDefault(account.getAccountNumber(), BigDecimal.ZERO);
            ledgerTotal = ledgerTotal.add(ledgerBalance);
            storedTotal = storedTotal.add(account.getBalance());

            BigDecimal difference = ledgerBalance.subtract(account.getBalance());
            if (difference.signum() != 0) {
                discrepancies.add(new Discrepancy(account.getAccountNumber(), account.getAccountName(),
                        ledgerBalance, account.getBalance(), difference));
            }
        }

        boolean truncated = discrepancies.size() > MAX_DISCREPANCIES_LISTED;
        List<Discrepancy> listed = truncated
                ? List.copyOf(discrepancies.subList(0, MAX_DISCREPANCIES_LISTED))
                : discrepancies;

        return new ReconciliationReport(discrepancies.isEmpty(), clock.instant(), storedAccounts.size(),
                ledgerTotal, storedTotal, listed, truncated);
    }

    /** Credits minus debits per account, from the two grouped ledger queries. */
    private Map<String, BigDecimal> ledgerTotals() {
        Map<String, BigDecimal> totals = new HashMap<>();
        for (AccountTypeTotal row : transactions.sumAmountByAccount(CREDIT_TYPES)) {
            totals.merge(row.getAccountNumber(), row.getTotal(), BigDecimal::add);
        }
        for (AccountTypeTotal row : transactions.sumAmountByAccount(DEBIT_TYPES)) {
            totals.merge(row.getAccountNumber(), row.getTotal().negate(), BigDecimal::add);
        }
        return totals;
    }

    private static void requireAdmin(CurrentUser caller) {
        if (caller == null || !caller.isAdmin()) {
            throw new AccessDeniedException("Only an ADMIN may run a reconciliation.");
        }
    }
}
