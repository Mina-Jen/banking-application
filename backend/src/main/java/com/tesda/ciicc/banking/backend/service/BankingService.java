package com.tesda.ciicc.banking.backend.service;

import com.tesda.ciicc.banking.backend.ai.CategorySuggestionClient;
import com.tesda.ciicc.banking.backend.auth.CurrentUser;
import com.tesda.ciicc.banking.backend.exception.AccountAlreadyExistsException;
import com.tesda.ciicc.banking.backend.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.backend.exception.AccountNumberAllocationException;
import com.tesda.ciicc.banking.backend.exception.BankingException;
import com.tesda.ciicc.banking.backend.exception.BankingValidationException;
import com.tesda.ciicc.banking.backend.exception.InvalidAmountException;
import com.tesda.ciicc.banking.backend.exception.TransactionNotFoundException;
import com.tesda.ciicc.banking.backend.model.Account;
import com.tesda.ciicc.banking.backend.model.AccountOwner;
import com.tesda.ciicc.banking.backend.model.Transaction;
import com.tesda.ciicc.banking.backend.model.TransactionCategory;
import com.tesda.ciicc.banking.backend.model.TransactionType;
import com.tesda.ciicc.banking.backend.repository.AccountOwnerRepository;
import com.tesda.ciicc.banking.backend.repository.AccountRepository;
import com.tesda.ciicc.banking.backend.repository.TransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
 *
 * <p>A denied CUSTOMER gets the same answer as an unknown account:
 * {@value #GENERIC_NOT_FOUND} with HTTP 404, and the account number never
 * appears in the message. Reporting "403, that account is not yours" would
 * let anyone walk through a range of numbers and learn which accounts exist —
 * an existence oracle, which is exactly what a bank API must not hand out.</p>
 */
@Service
public class BankingService {

    private static final int MAX_ACCOUNT_NUMBER_LENGTH = 20;   // VARCHAR(20)
    private static final int MAX_ACCOUNT_NAME_LENGTH = 100;    // VARCHAR(100)
    private static final int MAX_DESCRIPTION_LENGTH = 255;     // VARCHAR(255)
    private static final int MAX_INTEGER_DIGITS = 13;          // DECIMAL(15,2)
    private static final int MAX_PAGE_SIZE = 100;              // cap for every paginated endpoint

    /** How many numbers to draw before admitting the bank cannot hand one out. */
    private static final int MAX_ALLOCATION_ATTEMPTS = 5;

    /** The only 404 text a CUSTOMER ever sees for an account they cannot use. */
    static final String GENERIC_NOT_FOUND = "Account not found.";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final AccountOwnerRepository accountOwners;
    private final AuditService audit;
    private final CategorySuggestionClient categorySuggestions;
    private final AnomalyService anomalies;

    public BankingService(AccountRepository accounts, TransactionRepository transactions,
                          AccountOwnerRepository accountOwners, AuditService audit,
                          CategorySuggestionClient categorySuggestions, AnomalyService anomalies) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.accountOwners = accountOwners;
        this.audit = audit;
        this.categorySuggestions = categorySuggestions;
        this.anomalies = anomalies;
    }

    // =====================================================================
    //  Accounts
    // =====================================================================

    /**
     * Opens an account whose number the bank assigns, and links it to the
     * caller (a CUSTOMER owns the accounts they open; an ADMIN creates operator
     * accounts with no owner). A positive initial deposit is also recorded as
     * the account's first transaction.
     *
     * <p>Up to {@value #MAX_ALLOCATION_ATTEMPTS} numbers are drawn. A number
     * already in the table is simply drawn again, so a CUSTOMER never receives
     * the "already exists" 409 that the ADMIN-only endpoint can produce: an
     * answer that depends on whether a number is taken is a way to look
     * accounts up.</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Account createAccount(CurrentUser caller, String accountName, BigDecimal initialDeposit)
            throws BankingException {

        String name = requireAccountName(accountName);
        requireInitialDeposit(initialDeposit);

        for (int attempt = 1; attempt <= MAX_ALLOCATION_ATTEMPTS; attempt++) {
            String candidate = AccountNumberGenerator.generate();
            // The UNIQUE index is what really protects the data, but a plain
            // SELECT settles nearly every collision while the transaction is
            // still usable: a failed INSERT cannot be retried inside it.
            if (accounts.findByAccountNumber(candidate).isEmpty()) {
                try {
                    return insertAccount(caller, candidate, name, initialDeposit);
                } catch (AccountAlreadyExistsException lostRace) {
                    // Two sessions drew the same number in the same instant.
                    // Stop here: the INSERT that failed left this transaction
                    // marked for rollback, so a retry must be a new request.
                    throw allocationFailed();
                }
            }
        }
        throw allocationFailed();
    }

    /**
     * The ADMIN path: an operator may still choose the number, because an
     * operator needs to key an account opened on paper or in another system.
     * That also means the operator needs the 409 when the number is taken, and
     * a CUSTOMER is not allowed here at all — {@link #createAccount(CurrentUser,
     * String, BigDecimal)} is their only way in.
     */
    @Transactional(rollbackFor = Exception.class)
    public Account createAccount(CurrentUser caller, String accountNumber, String accountName,
                                 BigDecimal initialDeposit) throws BankingException {

        if (!caller.isAdmin()) {
            throw new AccessDeniedException(
                    "Only an ADMIN may choose an account number. The bank assigns it for customers.");
        }
        requireAccountNumber(accountNumber);
        String name = requireAccountName(accountName);
        requireInitialDeposit(initialDeposit);

        if (accounts.findByAccountNumber(accountNumber).isPresent()) {
            throw new AccountAlreadyExistsException("Account number " + accountNumber + " already exists.");
        }
        return insertAccount(caller, accountNumber, name, initialDeposit);
    }

    /**
     * Writes the account, its owner row, the audit entry and the opening
     * deposit. The caller must already have validated every argument and settled
     * how a duplicate number is reported — that answer differs between the two
     * public paths above.
     */
    private Account insertAccount(CurrentUser caller, String accountNumber, String accountName,
                                  BigDecimal initialDeposit) throws BankingException {

        // Two sessions can both pass a SELECT check; the UNIQUE index is what
        // actually protects the data, so a violation on this INSERT means duplicate.
        Account account = new Account(accountNumber, accountName, initialDeposit);
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
                    initialDeposit, initialDeposit, null, null);
        }
        // Re-read the stored row, so the caller sees the stored DECIMAL scale
        // and the values the database actually has.
        return accounts.findById(account.getId()).orElse(account);
    }

    private static AccountNumberAllocationException allocationFailed() {
        return new AccountNumberAllocationException(
                "Could not assign an account number. Please try again.");
    }

    /** An opening deposit may be zero — the account simply starts with no history. */
    private static void requireInitialDeposit(BigDecimal initialDeposit) throws InvalidAmountException {
        if (initialDeposit == null || initialDeposit.signum() < 0) {
            throw new InvalidAmountException("Initial deposit must be zero or greater.");
        }
        requireMoney(initialDeposit, "Initial deposit");
    }

    @Transactional(readOnly = true)
    public Account getAccount(CurrentUser caller, String accountNumber) throws BankingException {
        requireAccountNumber(accountNumber);
        // Ownership first: a CUSTOMER must not learn whether the number exists.
        requireOwnership(caller, accountNumber);
        return findAccount(accountNumber, caller);
    }

    /**
     * One page of the accounts the caller may see, ordered by account number.
     * A CUSTOMER gets exactly the accounts listed in {@code account_owners};
     * ADMIN is the operator role and gets every account. No ownership check per
     * account is needed because the CUSTOMER query already filters by user id —
     * an account simply never appears in another customer's list.
     */
    @Transactional(readOnly = true)
    public Page<Account> listAccounts(CurrentUser caller, int page, int size)
            throws BankingException {
        requirePage(page, size);
        Pageable pageable = PageRequest.of(page, size);
        if (caller.isAdmin()) {
            return accounts.findAllByOrderByAccountNumberAsc(pageable);
        }
        return accounts.findOwnedByUserId(caller.id(), pageable);
    }

    // =====================================================================
    //  Money movement
    // =====================================================================

    @Transactional(rollbackFor = Exception.class)
    public Transaction deposit(CurrentUser caller, String accountNumber, BigDecimal amount)
            throws BankingException {
        return deposit(caller, accountNumber, amount, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Transaction deposit(CurrentUser caller, String accountNumber, BigDecimal amount,
                               String description) throws BankingException {
        requireAccountNumber(accountNumber);
        requirePositiveMoney(amount, "Deposit amount");
        String note = requireDescription(description);

        // The ownership lookup does not lock, so it can run before SELECT ... FOR UPDATE.
        requireAccountHolder(caller, accountNumber, "deposit");
        Account account = findAccountForUpdate(accountNumber, caller);
        account.deposit(amount);
        requireStorableBalance(account.getBalance());
        Transaction transaction = recordTransaction(accountNumber, TransactionType.DEPOSIT,
                amount, account.getBalance(), null, note);
        audit.record(caller.username(), AuditService.DEPOSIT, accountNumber,
                "Deposited " + amount.toPlainString() + "; new balance "
                        + account.getBalance().toPlainString());
        return transaction;
    }

    @Transactional(rollbackFor = Exception.class)
    public Transaction withdraw(CurrentUser caller, String accountNumber, BigDecimal amount)
            throws BankingException {
        return withdraw(caller, accountNumber, amount, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Transaction withdraw(CurrentUser caller, String accountNumber, BigDecimal amount,
                                String description) throws BankingException {
        requireAccountNumber(accountNumber);
        requirePositiveMoney(amount, "Withdrawal amount");
        String note = requireDescription(description);

        requireAccountHolder(caller, accountNumber, "withdraw");
        Account account = findAccountForUpdate(accountNumber, caller);
        account.withdraw(amount);
        Transaction transaction = recordTransaction(accountNumber, TransactionType.WITHDRAW,
                amount, account.getBalance(), null, note);
        anomalies.evaluate(transaction);
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
     * The source check runs before either account is locked, and the locks
     * themselves stay in account-number order.
     *
     * <p>A destination that does not exist is still a 404 — the generic
     * {@value #GENERIC_NOT_FOUND} for a CUSTOMER — but only after the source has
     * been proved to hold the money, so an unfundable transfer cannot be used to
     * test whether a destination account number exists.</p>
     *
     * @return the transaction record of the source account
     */
    @Transactional(rollbackFor = Exception.class)
    public Transaction transfer(CurrentUser caller, String sourceAccount, String destinationAccount,
                                BigDecimal amount) throws BankingException {
        return transfer(caller, sourceAccount, destinationAccount, amount, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Transaction transfer(CurrentUser caller, String sourceAccount, String destinationAccount,
                                BigDecimal amount, String description) throws BankingException {

        requireAccountNumber(sourceAccount);
        requireAccountNumber(destinationAccount);
        requirePositiveMoney(amount, "Transfer amount");
        String note = requireDescription(description);
        if (sourceAccount.equals(destinationAccount)) {
            throw sameAccountError();
        }

        // Source ownership is settled before any row is locked. The lookup does
        // not lock, and the account-number ordering of the two locks below is
        // unchanged, so this cannot introduce a deadlock.
        requireAccountHolder(caller, sourceAccount, "transfer");

        // Lock in account-number order rather than source-first order, so two
        // opposite transfers cannot each hold one lock and wait for the other.
        // Ignore case because MySQL's account-number collation does: without
        // that, callers using different capitalization could choose opposite
        // lock orders for the same two rows. The two lookups stay separate on
        // purpose: one query with "in (...)" has no guaranteed lock order.
        boolean sourceLockedFirst =
                sourceAccount.compareToIgnoreCase(destinationAccount) < 0;
        Optional<Account> firstLocked =
                accounts.findByAccountNumberForUpdate(sourceLockedFirst ? sourceAccount : destinationAccount);
        Optional<Account> secondLocked =
                accounts.findByAccountNumberForUpdate(sourceLockedFirst ? destinationAccount : sourceAccount);
        Optional<Account> sourceRow = sourceLockedFirst ? firstLocked : secondLocked;
        Optional<Account> destinationRow = sourceLockedFirst ? secondLocked : firstLocked;

        // The source is resolved first: requireAccountHolder already answered a
        // foreign or missing source with the generic 404, so this only covers an
        // owner row whose account vanished in between.
        Account source = requireFound(sourceRow, sourceAccount, caller);

        // The equals() check above only catches identical strings. MySQL compares
        // account numbers case-insensitively, so "abc1" and "ABC1" are ONE row.
        // Comparing the database ids catches every spelling of the same account.
        if (destinationRow.isPresent() && source.getId() == destinationRow.get().getId()) {
            throw sameAccountError();
        }

        // Funds are checked before the destination is reported missing. If the
        // order were reversed, anyone with an empty account could walk a range of
        // numbers and read 404 (nobody has that account) against 422 (it exists)
        // — an existence oracle wearing a business error. The withdraw mutates
        // the locked row, and the 404 thrown after it rolls the whole transaction
        // back, so the source is unchanged either way.
        source.withdraw(amount);
        Account destination = requireFound(destinationRow, destinationAccount, caller);

        destination.deposit(amount);
        requireStorableBalance(destination.getBalance());

        Transaction sourceEntry = recordTransaction(sourceAccount, TransactionType.TRANSFER_OUT,
                amount, source.getBalance(), destinationAccount, note);
        recordTransaction(destinationAccount, TransactionType.TRANSFER_IN,
                amount, destination.getBalance(), sourceAccount, note);
        anomalies.evaluate(sourceEntry);
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
        requireOwnership(caller, accountNumber);
        findAccount(accountNumber, caller);   // ADMIN only: fails for unknown accounts
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
        requireOwnership(caller, accountNumber);
        findAccount(accountNumber, caller);   // ADMIN only: fails for unknown accounts
        requirePage(page, size);
        return transactions.findByAccountNumberOrderByIdAsc(accountNumber, PageRequest.of(page, size));
    }

    // =====================================================================
    //  Categories (Phase 4)
    // =====================================================================

    /**
     * Asks the configured suggester to file one of the caller's transactions.
     * Ownership is settled before the transaction row is read, so a foreign
     * account still answers with the generic 404. Only the transaction type,
     * amount and the caller-supplied description leave the bank — never the
     * account number, the customer or the running balance.
     *
     * <p>No service transaction wraps this call: the database row is read
     * first, then the model is called, so a slow upstream cannot hold a
     * connection or a lock.</p>
     */
    public TransactionCategory suggestCategory(CurrentUser caller, String accountNumber, int transactionId,
                                               String description) throws BankingException {
        requireAccountNumber(accountNumber);
        requireOwnership(caller, accountNumber);
        Transaction transaction = findTransaction(accountNumber, transactionId);
        return categorySuggestions.suggestCategory(new CategorySuggestionClient.CategorySuggestionInput(
                transaction.getTransactionType(), transaction.getAmount(), description));
    }

    /** Stores the category the owner accepted (or chose by hand) for one of their transactions. */
    @Transactional(rollbackFor = Exception.class)
    public Transaction setCategory(CurrentUser caller, String accountNumber, int transactionId,
                                   String categoryCode) throws BankingException {
        requireAccountNumber(accountNumber);
        requireOwnership(caller, accountNumber);
        TransactionCategory category = requireCategory(categoryCode);
        Transaction transaction = findTransaction(accountNumber, transactionId);
        transaction.setCategory(category);
        audit.record(caller.username(), AuditService.CATEGORY_SET, accountNumber,
                "Categorized transaction " + transaction.getTransactionReference() + " as " + category);
        return transaction;
    }

    // =====================================================================
    //  Ownership (Phase 2)
    // =====================================================================

    /**
     * Phase 2 rule: a CUSTOMER may only touch accounts listed in
     * {@code account_owners}; ADMIN is the operator role and passes. Runs
     * before any account lookup or lock, and answers the same way whether the
     * account is someone else's or does not exist at all, so the API cannot be
     * used to discover which account numbers are real.
     *
     * <p>The denial is audited first, in its own transaction (see
     * {@link AuditService#recordDeniedAccess}): the thrown exception makes
     * this transaction roll back, and the denial is exactly what must stay
     * visible in the audit trail.</p>
     */
    private void requireOwnership(CurrentUser caller, String accountNumber)
            throws AccountNotFoundException {
        if (caller.isAdmin()) {
            return;
        }
        if (!accountOwners.existsByUserIdAndAccountNumber(caller.id(), accountNumber)) {
            audit.recordDeniedAccess(caller, accountNumber);
            throw new AccountNotFoundException(GENERIC_NOT_FOUND);
        }
    }

    /**
     * Money moves only at the account holder's request. ADMIN may read every
     * account but is refused here with a 403, audited in its own transaction
     * like any other denial, so an operator cannot deposit, withdraw or
     * transfer on a customer's behalf.
     */
    private void requireAccountHolder(CurrentUser caller, String accountNumber, String operation)
            throws AccountNotFoundException {
        if (caller.isAdmin()) {
            audit.recordDeniedMoneyMovement(caller, accountNumber, operation);
            throw new AccessDeniedException(
                    "Administrators cannot " + operation + ". Only the account holder can move money.");
        }
        requireOwnership(caller, accountNumber);
    }

    // =====================================================================
    //  Lookup and persistence helpers
    // =====================================================================

    private Account findAccount(String accountNumber, CurrentUser caller)
            throws AccountNotFoundException {
        return requireFound(accounts.findByAccountNumber(accountNumber), accountNumber, caller);
    }

    /** The transaction must belong to the named account, not merely exist. */
    private Transaction findTransaction(String accountNumber, int transactionId)
            throws TransactionNotFoundException {
        return transactions.findByIdAndAccountNumber(transactionId, accountNumber)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found."));
    }

    /** Same as {@link #findAccount} but locks the row until the transaction ends. */
    private Account findAccountForUpdate(String accountNumber, CurrentUser caller)
            throws AccountNotFoundException {
        return requireFound(accounts.findByAccountNumberForUpdate(accountNumber), accountNumber, caller);
    }

    /**
     * ADMIN sees the account number in the message, which is what an operator
     * needs while debugging. A CUSTOMER only ever gets {@link #GENERIC_NOT_FOUND}
     * — the wording {@link #requireOwnership} already used for the same case.
     */
    private static Account requireFound(Optional<Account> account, String accountNumber,
                                        CurrentUser caller) throws AccountNotFoundException {
        return account.orElseThrow(() -> new AccountNotFoundException(
                caller.isAdmin() ? "Account " + accountNumber + " does not exist." : GENERIC_NOT_FOUND));
    }

    private Transaction recordTransaction(String accountNumber, TransactionType type,
                                          BigDecimal amount, BigDecimal balanceAfter,
                                          String referenceAccount, String description) {
        Transaction transaction = new Transaction();
        transaction.setTransactionReference(generateReference());
        transaction.setAccountNumber(accountNumber);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setBalance(balanceAfter);
        transaction.setReferenceAccount(referenceAccount);
        transaction.setDescription(description);
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

    /** Shared by every paginated endpoint, so a bad page or size reads the same anywhere. */
    private static void requirePage(int page, int size) throws BankingValidationException {
        if (page < 0) {
            throw new BankingValidationException("Page must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BankingValidationException("Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
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

    /** A description is optional: blank becomes null, and the stored note is trimmed. */
    private static String requireDescription(String description) throws BankingValidationException {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BankingValidationException(
                    "Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static TransactionCategory requireCategory(String categoryCode)
            throws BankingValidationException {
        return TransactionCategory.fromCode(categoryCode).orElseThrow(() ->
                new BankingValidationException(
                        "Category must be one of: " + TransactionCategory.allowedCodes() + "."));
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
