package service;

import dao.AccountDAO;
import dao.TransactionDAO;
import exception.AccountAlreadyExistsException;
import exception.AccountNotFoundException;
import exception.BankingValidationException;
import exception.InsufficientFundsException;
import exception.InvalidAmountException;
import model.Account;
import model.Transaction;
import model.TransactionType;
import util.ConnectionProvider;
import util.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;

/**
 * Business rules of the banking application. Each method owns its database
 * connection and commits only when every write of that operation succeeded.
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

    public Account createAccount(String accountNumber, String accountName, BigDecimal initialDeposit)
            throws SQLException, AccountAlreadyExistsException, BankingValidationException, InvalidAmountException {

        requireAccountNumber(accountNumber);
        if (accountName == null || accountName.isBlank()) {
            throw new BankingValidationException("Account holder name must not be empty.");
        }
        if (accountName.length() > MAX_ACCOUNT_NAME_LENGTH) {
            throw new BankingValidationException(
                    "Account holder name must be at most " + MAX_ACCOUNT_NAME_LENGTH + " characters.");
        }
        if (initialDeposit == null || initialDeposit.signum() < 0) {
            throw new InvalidAmountException("Initial deposit must be zero or greater.");
        }
        requireMoney(initialDeposit, "Initial deposit");

        try (Connection conn = connections.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (accountDao.findByAccountNumber(conn, accountNumber) != null) {
                    throw new AccountAlreadyExistsException(
                            "Account number " + accountNumber + " already exists.");
                }
                accountDao.insert(conn, new Account(accountNumber, accountName.trim(), initialDeposit));
                if (initialDeposit.signum() > 0) {
                    // The opening deposit is the first line of the account's history.
                    insertTransaction(conn, accountNumber, TransactionType.DEPOSIT,
                            initialDeposit, initialDeposit, null);
                }
                conn.commit();
            } catch (SQLIntegrityConstraintViolationException e) {
                // Two sessions can both pass the existence check above; the
                // UNIQUE index is what actually protects the data.
                conn.rollback();
                throw new AccountAlreadyExistsException(
                        "Account number " + accountNumber + " already exists.");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
            return accountDao.findByAccountNumber(conn, accountNumber);
        }
    }

    public Account getAccount(String accountNumber)
            throws SQLException, AccountNotFoundException, BankingValidationException {

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

    public Transaction deposit(String accountNumber, BigDecimal amount)
            throws SQLException, AccountNotFoundException, InvalidAmountException, BankingValidationException {

        requireAccountNumber(accountNumber);
        requirePositiveMoney(amount, "Deposit amount");

        try (Connection conn = connections.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account account = findAccountForUpdate(conn, accountNumber);
                BigDecimal newBalance = account.getBalance().add(amount);
                requireStorableBalance(newBalance);
                accountDao.updateBalance(conn, accountNumber, newBalance);
                Transaction saved = insertTransaction(
                        conn, accountNumber, TransactionType.DEPOSIT, amount, newBalance, null);
                conn.commit();
                return saved;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public Transaction withdraw(String accountNumber, BigDecimal amount)
            throws SQLException, AccountNotFoundException, InvalidAmountException,
                   InsufficientFundsException, BankingValidationException {

        requireAccountNumber(accountNumber);
        requirePositiveMoney(amount, "Withdrawal amount");

        try (Connection conn = connections.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Account account = findAccountForUpdate(conn, accountNumber);
                if (amount.compareTo(account.getBalance()) > 0) {
                    throw new InsufficientFundsException("Withdrawal of " + amount.toPlainString()
                            + " exceeds the available balance of " + account.getBalance().toPlainString() + ".");
                }
                BigDecimal newBalance = account.getBalance().subtract(amount);
                accountDao.updateBalance(conn, accountNumber, newBalance);
                Transaction saved = insertTransaction(
                        conn, accountNumber, TransactionType.WITHDRAW, amount, newBalance, null);
                conn.commit();
                return saved;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Moves money between two accounts as one database transaction and records
     * it on both accounts, so each account's history explains its own balance.
     */
    public Transaction transfer(String sourceAccount, String destinationAccount, BigDecimal amount)
            throws SQLException, AccountNotFoundException, InvalidAmountException,
                   InsufficientFundsException, BankingValidationException {

        requireAccountNumber(sourceAccount);
        requireAccountNumber(destinationAccount);
        requirePositiveMoney(amount, "Transfer amount");
        if (sourceAccount.equals(destinationAccount)) {
            throw new BankingValidationException(
                    "Source and destination accounts must be different.");
        }

        try (Connection conn = connections.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Locked in account-number order rather than source-first order, so two
                // opposite transfers cannot each hold one lock and wait for the other.
                boolean sourceLockedFirst = sourceAccount.compareTo(destinationAccount) < 0;
                Account firstLocked = findAccountForUpdate(
                        conn, sourceLockedFirst ? sourceAccount : destinationAccount);
                Account secondLocked = findAccountForUpdate(
                        conn, sourceLockedFirst ? destinationAccount : sourceAccount);
                Account source = sourceLockedFirst ? firstLocked : secondLocked;
                Account destination = sourceLockedFirst ? secondLocked : firstLocked;

                if (amount.compareTo(source.getBalance()) > 0) {
                    throw new InsufficientFundsException("Transfer of " + amount.toPlainString()
                            + " exceeds the available balance of " + source.getBalance().toPlainString() + ".");
                }
                BigDecimal sourceBalance = source.getBalance().subtract(amount);
                BigDecimal destinationBalance = destination.getBalance().add(amount);
                requireStorableBalance(destinationBalance);

                accountDao.updateBalance(conn, sourceAccount, sourceBalance);
                accountDao.updateBalance(conn, destinationAccount, destinationBalance);
                Transaction sourceEntry = insertTransaction(
                        conn, sourceAccount, TransactionType.TRANSFER, amount, sourceBalance, destinationAccount);
                insertTransaction(
                        conn, destinationAccount, TransactionType.TRANSFER, amount, destinationBalance, sourceAccount);
                conn.commit();
                return sourceEntry;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public List<Transaction> getTransactionHistory(String accountNumber)
            throws SQLException, AccountNotFoundException, BankingValidationException {

        requireAccountNumber(accountNumber);
        try (Connection conn = connections.getConnection()) {
            findAccount(conn, accountNumber);
            return transactionDao.findByAccountNumber(conn, accountNumber);
        }
    }

    // ---------- helpers ----------

    private Account findAccount(Connection conn, String accountNumber)
            throws SQLException, AccountNotFoundException {
        Account account = accountDao.findByAccountNumber(conn, accountNumber);
        if (account == null) {
            throw new AccountNotFoundException("Account " + accountNumber + " does not exist.");
        }
        return account;
    }

    private Account findAccountForUpdate(Connection conn, String accountNumber)
            throws SQLException, AccountNotFoundException {
        Account account = accountDao.findByAccountNumberForUpdate(conn, accountNumber);
        if (account == null) {
            throw new AccountNotFoundException("Account " + accountNumber + " does not exist.");
        }
        return account;
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

    private static void requireAccountNumber(String accountNumber) throws BankingValidationException {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BankingValidationException("Account number must not be empty.");
        }
        if (accountNumber.length() > MAX_ACCOUNT_NUMBER_LENGTH) {
            throw new BankingValidationException(
                    "Account number must be at most " + MAX_ACCOUNT_NUMBER_LENGTH + " characters.");
        }
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

    private static void requirePositiveMoney(BigDecimal amount, String label) throws InvalidAmountException {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException(label + " must be greater than zero.");
        }
        requireMoney(amount, label);
    }

    private static void requireStorableBalance(BigDecimal value) throws InvalidAmountException {
        if (value.precision() - value.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidAmountException(
                    "Amount is too large: a balance may have at most " + MAX_INTEGER_DIGITS + " digits.");
        }
    }
}
