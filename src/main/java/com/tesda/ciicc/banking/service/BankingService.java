package com.tesda.ciicc.banking.service;

import com.tesda.ciicc.banking.dao.AccountDAO;
import com.tesda.ciicc.banking.dao.TransactionDAO;
import com.tesda.ciicc.banking.exception.AccountAlreadyExistsException;
import com.tesda.ciicc.banking.exception.AccountNotFoundException;
import com.tesda.ciicc.banking.exception.BankingException;
import com.tesda.ciicc.banking.exception.BankingValidationException;
import com.tesda.ciicc.banking.exception.InvalidAmountException;
import com.tesda.ciicc.banking.model.Account;
import com.tesda.ciicc.banking.model.Transaction;
import com.tesda.ciicc.banking.model.TransactionType;
import com.tesda.ciicc.banking.util.ConnectionProvider;
import com.tesda.ciicc.banking.util.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

/**
 * Business rules of the banking application. Every operation that changes data
 * runs inside one database transaction: it is committed only when every write
 * succeeded, and rolled back otherwise.
 */
public class BankingService {

    private static final int MAX_ACCOUNT_NUMBER_LENGTH = 20;   // VARCHAR(20)
    private static final int MAX_ACCOUNT_NAME_LENGTH = 100;    // VARCHAR(100)
    private static final int MAX_INTEGER_DIGITS = 13;          // DECIMAL(15,2)

    private final AccountDAO accountDao = new AccountDAO();
    private final TransactionDAO transactionDao = new TransactionDAO();
    private final ConnectionProvider connections;

    public BankingService() {
        this(DatabaseConnection::getConnection);
    }

    public BankingService(ConnectionProvider connections) {
        this.connections = connections;
    }

    // =====================================================================
    //  Accounts
    // =====================================================================

    /** Creates an account. A positive initial deposit is also recorded as its first transaction. */
    public Account createAccount(String accountNumber, String accountName, BigDecimal initialDeposit)
            throws SQLException, BankingException {

        requireAccountNumber(accountNumber);
        String name = requireAccountName(accountName);
        if (initialDeposit == null || initialDeposit.signum() < 0) {
            throw new InvalidAmountException("Initial deposit must be zero or greater.");
        }
        requireMoney(initialDeposit, "Initial deposit");

        return inTransaction(conn -> {
            if (accountDao.findByAccountNumber(conn, accountNumber) != null) {
                throw new AccountAlreadyExistsException("Account number " + accountNumber + " already exists.");
            }
            insertAccount(conn, new Account(accountNumber, name, initialDeposit));
            if (initialDeposit.signum() > 0) {
                insertTransaction(conn, accountNumber, TransactionType.DEPOSIT,
                        initialDeposit, initialDeposit, null);
            }
            return accountDao.findByAccountNumber(conn, accountNumber);
        });
    }

    public Account getAccount(String accountNumber) throws SQLException, BankingException {
        requireAccountNumber(accountNumber);
        try (Connection conn = connections.getConnection()) {
            return findAccount(conn, accountNumber);
        }
    }

    public List<Account> listAccounts() throws SQLException {
        try (Connection conn = connections.getConnection()) {
            return accountDao.findAll(conn);
        }
    }

    // =====================================================================
    //  Money movement
    // =====================================================================

    public Transaction deposit(String accountNumber, BigDecimal amount)
            throws SQLException, BankingException {

        requireAccountNumber(accountNumber);
        requirePositiveMoney(amount, "Deposit amount");

        return inTransaction(conn -> {
            Account account = findAccountForUpdate(conn, accountNumber);
            account.deposit(amount);
            requireStorableBalance(account.getBalance());
            accountDao.updateBalance(conn, account.getId(), account.getBalance());
            return insertTransaction(conn, accountNumber, TransactionType.DEPOSIT,
                    amount, account.getBalance(), null);
        });
    }

    public Transaction withdraw(String accountNumber, BigDecimal amount)
            throws SQLException, BankingException {

        requireAccountNumber(accountNumber);
        requirePositiveMoney(amount, "Withdrawal amount");

        return inTransaction(conn -> {
            Account account = findAccountForUpdate(conn, accountNumber);
            account.withdraw(amount);
            accountDao.updateBalance(conn, account.getId(), account.getBalance());
            return insertTransaction(conn, accountNumber, TransactionType.WITHDRAW,
                    amount, account.getBalance(), null);
        });
    }

    /**
     * Moves money between two accounts as one database transaction and records
     * it on both accounts, so each account's history explains its own balance.
     *
     * @return the transaction record of the source account
     */
    public Transaction transfer(String sourceAccount, String destinationAccount, BigDecimal amount)
            throws SQLException, BankingException {

        requireAccountNumber(sourceAccount);
        requireAccountNumber(destinationAccount);
        requirePositiveMoney(amount, "Transfer amount");
        if (sourceAccount.equals(destinationAccount)) {
            throw sameAccountError();
        }

        return inTransaction(conn -> {
            // Lock in account-number order rather than source-first order, so two
            // opposite transfers cannot each hold one lock and wait for the other.
            boolean sourceLockedFirst = sourceAccount.compareTo(destinationAccount) < 0;
            Account firstLocked = findAccountForUpdate(
                    conn, sourceLockedFirst ? sourceAccount : destinationAccount);
            Account secondLocked = findAccountForUpdate(
                    conn, sourceLockedFirst ? destinationAccount : sourceAccount);
            Account source = sourceLockedFirst ? firstLocked : secondLocked;
            Account destination = sourceLockedFirst ? secondLocked : firstLocked;

            // The equals() check above only catches identical strings. MySQL compares
            // account numbers case-insensitively, so "abc1" and "ABC1" are ONE row.
            // Comparing the database ids catches every spelling of the same account.
            if (source.getId() == destination.getId()) {
                throw sameAccountError();
            }

            source.withdraw(amount);
            destination.deposit(amount);
            requireStorableBalance(destination.getBalance());

            accountDao.updateBalance(conn, source.getId(), source.getBalance());
            accountDao.updateBalance(conn, destination.getId(), destination.getBalance());
            Transaction sourceEntry = insertTransaction(conn, sourceAccount, TransactionType.TRANSFER_OUT,
                    amount, source.getBalance(), destinationAccount);
            insertTransaction(conn, destinationAccount, TransactionType.TRANSFER_IN,
                    amount, destination.getBalance(), sourceAccount);
            return sourceEntry;
        });
    }

    // =====================================================================
    //  History
    // =====================================================================

    public List<Transaction> getTransactionHistory(String accountNumber)
            throws SQLException, BankingException {

        requireAccountNumber(accountNumber);
        try (Connection conn = connections.getConnection()) {
            findAccount(conn, accountNumber);   // fails with "does not exist" for unknown accounts
            return transactionDao.findByAccountNumber(conn, accountNumber);
        }
    }

    // =====================================================================
    //  Database transaction handling
    // =====================================================================

    /** A piece of work that runs on one connection, inside one database transaction. */
    @FunctionalInterface
    private interface TransactionalWork<T> {
        T run(Connection conn) throws SQLException, BankingException;
    }

    /**
     * Opens a connection, runs the work, and commits. If anything fails, the
     * work is rolled back and the original exception is rethrown.
     */
    private <T> T inTransaction(TransactionalWork<T> work) throws SQLException, BankingException {
        try (Connection conn = connections.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = work.run(conn);
                conn.commit();
                return result;
            } catch (SQLException | BankingException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackFailure) {
                    e.addSuppressed(rollbackFailure);   // never hide the real error
                }
                throw e;
            }
        }
    }

    // =====================================================================
    //  Lookup and persistence helpers
    // =====================================================================

    private Account findAccount(Connection conn, String accountNumber)
            throws SQLException, AccountNotFoundException {
        return requireFound(accountDao.findByAccountNumber(conn, accountNumber), accountNumber);
    }

    /** Same as {@link #findAccount} but locks the row until the transaction ends. */
    private Account findAccountForUpdate(Connection conn, String accountNumber)
            throws SQLException, AccountNotFoundException {
        return requireFound(accountDao.findByAccountNumberForUpdate(conn, accountNumber), accountNumber);
    }

    private static Account requireFound(Account account, String accountNumber)
            throws AccountNotFoundException {
        if (account == null) {
            throw new AccountNotFoundException("Account " + accountNumber + " does not exist.");
        }
        return account;
    }

    /**
     * Two sessions can both pass the "already exists" check; the UNIQUE index is
     * what actually protects the data, so a violation on this INSERT means duplicate.
     */
    private void insertAccount(Connection conn, Account account)
            throws SQLException, AccountAlreadyExistsException {
        try {
            accountDao.insert(conn, account);
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new AccountAlreadyExistsException(
                    "Account number " + account.getAccountNumber() + " already exists.");
        }
    }

    private Transaction insertTransaction(Connection conn, String accountNumber, TransactionType type,
                                          BigDecimal amount, BigDecimal balanceAfter,
                                          String referenceAccount) throws SQLException {
        Transaction transaction = new Transaction();
        transaction.setTransactionReference(transactionDao.generateReference(conn));
        transaction.setAccountNumber(accountNumber);
        transaction.setTransactionType(type);
        transaction.setAmount(amount);
        transaction.setBalance(balanceAfter);
        transaction.setReferenceAccount(referenceAccount);
        transactionDao.insert(conn, transaction);
        return transaction;
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