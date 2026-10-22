package com.tesda.ciicc.banking.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "transaction_reference", nullable = false, unique = true, length = 20)
    private String transactionReference;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    // The schema uses a native ENUM. Mapping it as a string keeps the Java
    // names in the column and works with both MySQL Connector/J and MariaDB
    // Connector/J; Hibernate's NAMED_ENUM mapping failed at runtime on MariaDB.
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false,
            columnDefinition = "enum('DEPOSIT','WITHDRAW','TRANSFER_OUT','TRANSFER_IN')")
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;         // balance AFTER the transaction

    @Column(name = "reference_account", length = 20)
    private String referenceAccount;    // other account in a transfer, else null

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    public Transaction() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getReferenceAccount() { return referenceAccount; }
    public void setReferenceAccount(String referenceAccount) { this.referenceAccount = referenceAccount; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}
