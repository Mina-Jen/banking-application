package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.AccountAlreadyExistsException;
import com.tesda.ciicc.banking.backend.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.exception.InvalidAmountException;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.model.AccountOwner;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.repository.AccountOwnerRepository;
import com.tesda.ciicc.banking.backend.repository.AccountRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

/**
 * Business rules of the banking application. Every operation that changes data
 * runs inside one database transaction: it is committed only when every write
 * succeeded, and rolled back otherwise.
 *
 * <p>The domain exceptions are checked, so mutating methods opt into rollback
 * for every exception; Spring only rolls back runtime exceptions by default.
 * </p>
 *
 * <p>Since Phase 2 every method also receives the authenticated
 * {@link CurrentUser}: a CUSTOMER may only touch accounts listed in
 * {@code account_owners}, ADMIN (the operator role) may touch all accounts.
 * The check lives here in the service layer on purpose, so the rule holds for
 * every caller, not only for the HTTP controllers.</p>
 */
@Service
public class BankingService {

    private static final int MAX_ACCOUNT_NUMBER_LENGTH = 20;   // VARCHAR(20)
    private static final int MAX_ACCOUNT_NAME_LENGTH = 100;    // VARCHAR(100)
    private static final int MAX_INTEGER_DIGITS = 13;          // DECIMAL(15,2)
    private static final int MAX_PAGE_SIZE = 100;              // history page size

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AccountOwnerRepository accountOwners;
    private final AuditService audit;

    public BankingService(AccountRepository accounts, TransactionRepository transactions,
                          AccountOwnerRepository accountOwners, AuditService audit) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.accountOwners = accountOwners;
        this.audit = audit;
    }

    // =====================================================================
    //  Accounts
    // =====================================================================

    /**
     * Creates an account and links it to the caller (a CUSTOMER owns the
     * accounts they create; an ADMIN creates operator accounts with no owner).
     * A positive initial deposit is also recorded as the account's first
     * transaction.
     */
    @Transactional(rollbackFor = Exception.class)
    public Account createAccount(CurrentUser caller, String accountNumber, String accountName,
                                 BigDecimal initialDeposit) throws BankingException {

        requireAccountNumber(accountNumber);
        String name = requireAccountName(accountName);
        if (initialDeposit == null || initialDeposit.signum() < 0) {
            throw new InvalidAmountException("Initial deposit must be zero or greater.");
        }
        requireMoney(initialDeposit, "Initial deposit");

        if (accounts.findByAccountNumber(accountNumber).isPresent()) {
            throw new AccountAlreadyExistsException("Account number " + accountNumber + " already exists.");
        }

        // Two sessions can both pass the check above; the UNIQUE index is what
        // actually protects the data, so a violation on this INSERT means duplicate.
        Account account = new Account(accountNumber, name, initialDeposit);
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new AccountAlreadyExistsException("Account number " + accountNumber + " already exists.");
        }

        if (!caller.isAdmin()) {
            // The account row was flushed above, so this foreign key resolves.
            accountOwners.save(new AccountOwner(caller.id(), accountNumber));
        }
        audit.record(caller.username(), AuditService.ACCOUNT_CREATED, accountNumber,
                "Created by " + caller.username() + " (" + caller.role() + ")");

        if (initialDeposit.signum() > 0) {
            recordTransaction(accountNumber, TransactionType.DEPOSIT,
                    initialDeposit, initialDeposit, null);
        }
        // Re-read the stored row, so the caller sees the stored DECIMAL scale
        // and the values the database actually has.
        return accounts.findById(account.getId()).orElse(account);
    }

    @Transactional(readOnly = true)
    public Account getAccount(CurrentUser caller, String accountNumber) throws BankingException {
        requireAccountNumber(accountNumber);
        Account account = findAccount(accountNumber);
        requireAccess(caller, accountNumber);
        return account;
    }

    @Transactional(readOnly = true)
    public List<Account> listAccounts() {
        return accounts.findAllByOrderByAccountNumberAsc();
    }

    // =====================================================================
    //  Money movement
    // =====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Transaction deposit(CurrentUser caller, String accountNumber, BigDecimal amount)
            throws BankingException {
        requireAccountNumber(accountNumber);
        requirePositiveMoney(amount, "Deposit amount");

        Account account = findAccountForUpdate(accountNumber);
        requireAccess(caller, accountNumber);
        account.deposit(amount);
        requireStorableBalance(account.getBalance());
        Transaction transaction = recordTransaction(accountNumber, TransactionType.DEPOSIT,
                amount, account.getBalance(), null);
        audit.record(caller.username(), AuditService.DEPOSIT, accountNumber,
                "Deposited " + amount.toPlainString() + "; new balance "
                        + account.getBalance().toPlainString());
        return transaction;
    }

    @Transactional(rollbackFor = Exception.class)
    public Transaction withdraw(CurrentUser caller, String accountNumber, BigDecimal amount)
            throws BankingException {
        requireAccountNumber(accountNumber);
        requirePositiveMoney(amount, "Withdrawal amount");

        Account account = findAccountForUpdate(accountNumber);
        requireAccess(caller, accountNumber);
        account.withdraw(amount);
        Transaction transaction = recordTransaction(accountNumber, TransactionType.WITHDRAW,
                amount, account.getBalance(), null);
        audit.record(caller.username(), AuditService.WITHDRAW, accountNumber,
                "Withdrew " + amount.toPlainString() + "; new balance "
                        + account.getBalance().toPlainString());
        return transaction;
    }

    /**
     * Moves money between two accounts as one database transaction and records
     * it on both accounts, so each account's history explains its own balance.
     * The caller must own the SOURCE account; the destination may belong to
     * anyone, exactly like depositing into a friend's account at a real bank.
     *
     * @return the transaction record of the source account
     */
    @Transactional(rollbackFor = Exception.class)
    public Transaction transfer(CurrentUser caller, String sourceAccount, String destinationAccount,
                                BigDecimal amount) throws BankingException {

        requireAccountNumber(sourceAccount);
        requireAccountNumber(destinationAccount);
        requirePositiveMoney(amount, "Transfer amount");
        if (sourceAccount.equals(destinationAccount)) {
            throw sameAccountError();
        }

        // Lock in account-number order rather than source-first order, so two
        // opposite transfers cannot each hold one lock and wait for the other.
        // Ignore case because MySQL's account-number collation does: without
        // that, callers using different capitalization could choose opposite
        // lock orders for the same two rows. The two lookups stay separate on
        // purpose: one query with "in (...)" has no guaranteed lock order.
        boolean sourceLockedFirst =
                sourceAccount.compareToIgnoreCase(destinationAccount) < 0;
        Account firstLocked = findAccountForUpdate(
                sourceLockedFirst ? sourceAccount : destinationAccount);
        Account secondLocked = findAccountForUpdate(
                sourceLockedFirst ? destinationAccount : sourceAccount);
        Account source = sourceLockedFirst ? firstLocked : secondLocked;
        Account destination = sourceLockedFirst ? secondLocked : firstLocked;

        // The equals() check above only catches identical strings. MySQL compares
        // account numbers case-insensitively, so "abc1" and "ABC1" are ONE row.
        // Comparing the database ids catches every spelling of the same account.
        if (source.getId() == destination.getId()) {
            throw sameAccountError();
        }

        requireAccess(caller, sourceAccount);

        source.withdraw(amount);
        destination.deposit(amount);
        requireStorableBalance(destination.getBalance());

        Transaction sourceEntry = recordTransaction(sourceAccount, TransactionType.TRANSFER_OUT,
                amount, source.getBalance(), destinationAccount);
        recordTransaction(destinationAccount, TransactionType.TRANSFER_IN,
                amount, destination.getBalance(), sourceAccount);
        audit.record(caller.username(), AuditService.TRANSFER, sourceAccount,
                "Transferred " + amount.toPlainString() + " to " + destinationAccount
                        + "; new balance " + source.getBalance().toPlainString());
        return sourceEntry;
    }

    // =====================================================================
    //  History
    // =====================================================================

    @Transactional(readOnly = true)
    public List<Transaction> getTransactionHistory(CurrentUser caller, String accountNumber)
            throws BankingException {
        requireAccountNumber(accountNumber);
        findAccount(accountNumber);   // fails with "does not exist" for unknown accounts
        requireAccess(caller, accountNumber);
        return transactions.findByAccountNumberOrderByIdAsc(accountNumber);
    }

    /**
     * One page of the account's transactions, oldest first. The account must
     * exist; {@code page} counts from zero and {@code size} is capped at
     * {@value #MAX_PAGE_SIZE}.
     */
    @Transactional(readOnly = true)
    public Page<Transaction> getTransactionHistoryPage(CurrentUser caller, String accountNumber,
                                                       int page, int size) throws BankingException {

        requireAccountNumber(accountNumber);
        findAccount(accountNumber);   // fails with "does not exist" for unknown accounts
        requireAccess(caller, accountNumber);
        if (page < 0) {
            throw new BankingValidationException("Page must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BankingValidationException("Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        return transactions.findByAccountNumberOrderByIdAsc(accountNumber, PageRequest.of(page, size));
    }

    // =====================================================================
    //  Ownership (Phase 2)
    // =====================================================================

    /**
     * Phase 2 rule: a CUSTOMER may only touch accounts listed in
     * {@code account_owners}; ADMIN is the operator role and passes. The
     * denial is audited first, in its own transaction (see
     * {@link AuditService#recordDeniedAccess}): the thrown exception makes
     * this transaction roll back, and the denial is exactly what must stay
     * visible in the audit trail.
     */
    private void requireAccess(CurrentUser caller, String accountNumber) {
        if (caller.isAdmin()) {
            return;
        }
        if (!accountOwners.existsByUserIdAndAccountNumber(caller.id(), accountNumber)) {
            audit.recordDeniedAccess(caller, accountNumber);
            throw new AccessDeniedException("You are not allowed to access account " + accountNumber + ".");
        }
    }

    // =====================================================================
    //  Lookup and persistence helpers
    // =====================================================================

    private Account findAccount(String accountNumber) throws AccountNotFoundException {
        return requireFound(accounts.findByAccountNumber(accountNumber), accountNumber);
    }

    /** Same as {@link #findAccount} but locks the row until the transaction ends. */
    private Account findAccountForUpdate(String accountNumber) throws AccountNotFoundException {
        return requireFound(accounts.findByAccountNumberForUpdate(accountNumber), accountNumber);
    }

    private static Account requireFound(Optional<Account> account, String accountNumber)
            throws AccountNotFoundException {
        return account.orElseThrow(() -> new AccountNotFoundException(
                "Account " + accountNumber + " does not exist."));
    }

    private Transaction recordTransaction(String accountNumber, TransactionType type,
                                          BigDecimal amount, BigDecimal balanceAfter,
                                          String referenceAccount) {
        Transaction transaction = new Transaction();
        transaction.setTransactionReference(generateReference());
        transaction.setAccountNumber(accountNumber);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setBalance(balanceAfter);
        transaction.setReferenceAccount(referenceAccount);
        return transactions.save(transaction);
    }

    // Builds a reference without reading the transactions table, so no row or
    // table lock is needed: "TXN-" + current time + random suffix, both in
    // base 36 (4 + 8 + 6 = 18 chars, fits VARCHAR(20)). The UNIQUE index on
    // transaction_reference is the final safety net.
    private static String generateReference() {
        String suffix = Long.toString(System.currentTimeMillis(), 36) + randomBase36(6);
        return "TXN-" + suffix;
    }

    private static String randomBase36(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(Character.forDigit(RANDOM.nextInt(36), 36));
        }
        return sb.toString();
    }

    // =====================================================================
    //  Validation
    // =====================================================================

    private static BankingValidationException sameAccountError() {
        return new BankingValidationException("Source and destination accounts must be different.");
    }

    private static void requireAccountNumber(String accountNumber) throws BankingValidationException {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BankingValidationException("Account number must not be empty.");
        }
        if (accountNumber.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new BankingValidationException(
                    "Account number must be at most " + MAX_ACCOUNT_NUMBER_LENGTH + " characters.");
        }
    }

    /** Returns the trimmed name; the length limit applies to what is actually stored. */
    private static String requireAccountName(String accountName) throws BankingValidationException {
        if (accountName == null || accountName.isBlank()) {
            throw new BankingValidationException("Account holder name must not be empty.");
        }
        String trimmed = accountName.trim();
        if (trimmed.length() > MAX_ACCOUNT_NAME_LENGTH) {
            throw new BankingValidationException(
                    "Account holder name must be at most " + MAX_ACCOUNT_NAME_LENGTH + " characters.");
        }
        return trimmed;
    }

    /** An amount that is greater than zero and storable (see {@link #requireMoney}). */
    private static void requirePositiveMoney(BigDecimal amount, String label) throws InvalidAmountException {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException(label + " must be greater than zero.");
        }
        requireMoney(amount, label);
    }

    /**
     * Rejects amounts MySQL would silently round: DECIMAL(15,2) keeps two
     * decimals, so 0.001 would be stored as 0.00 and the money would disappear.
     */
    private static void requireMoney(BigDecimal amount, String label) throws InvalidAmountException {
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new InvalidAmountException(label + " must not use more than two decimal places.");
        }
        requireStorableBalance(amount);
    }

    private static void requireStorableBalance(BigDecimal value) throws InvalidAmountException {
        if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidAmountException(
                    "Amount is too large: a balance may have at most " + MAX_INTEGER_DIGITS + " digits.");
        }
    }
}
