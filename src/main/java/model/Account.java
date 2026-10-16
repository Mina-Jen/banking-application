package model;

import exception.InsufficientFundsException;
import exception.InvalidAmountException;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class Account {
    private int id;
    private String accountNumber;
    private String accountName;
    private BigDecimal balance;
    private Timestamp createdAt;

    public Account(String accountNumber, String accountName, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.balance = balance;
    }

    public Account(int id, String accountNumber, String accountName,
                   BigDecimal balance, Timestamp createdAt) {
        this(accountNumber, accountName, balance);
        this.id = id;
        this.createdAt = createdAt;
    }

    // ---------- behavior: the account protects its own balance ----------

    public void deposit(BigDecimal amount) throws InvalidAmountException {
        requirePositive(amount);
        balance = balance.add(amount);
    }

    public void withdraw(BigDecimal amount) throws InvalidAmountException, InsufficientFundsException {
        requirePositive(amount);
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException("Amount " + amount.toPlainString()
                    + " exceeds the available balance of " + balance.toPlainString() + ".");
        }
        balance = balance.subtract(amount);
    }

    private static void requirePositive(BigDecimal amount) throws InvalidAmountException {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero.");
        }
    }


    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public BigDecimal getBalance() { return balance; }
    // setBalance removed on purpose: balance changes only through deposit() and withdraw()

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}