package com.tesda.ciicc.banking.backend.model;

import com.tesda.ciicc.banking.backend.exception.InsufficientFundsException;
import com.tesda.ciicc.banking.backend.exception.InvalidAmountException;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Column(name = "balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    /** For JPA only. */
    protected Account() {
    }

    public Account(String accountNumber, String accountName, BigDecimal balance) {
        this.accountNumber = accountNumber;
        this.accountName = accountName;
        this.balance = balance;
    }

    /** Full constructor, used when mapping a stored row (e.g. in tests). */
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

    public String getAccountNumber() { return accountNumber; }

    public String getAccountName() { return accountName; }

    public BigDecimal getBalance() { return balance; }
    // setBalance removed on purpose: balance changes only through deposit() and withdraw()

    public Timestamp getCreatedAt() { return createdAt; }
}
