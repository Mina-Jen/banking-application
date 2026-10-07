package model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class Transaction {
    private int id;
    private String transactionReference;
    private String accountNumber;
    private String transactionType;     // DEPOSIT, WITHDRAW, TRANSFER
    private BigDecimal amount;
    private BigDecimal balance;         // balance AFTER the transaction
    private String referenceAccount;    // other account in a transfer, else null
    private Timestamp createdAt;

    public Transaction() {}

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getReferenceAccount() { return referenceAccount; }
    public void setReferenceAccount(String referenceAccount) { this.referenceAccount = referenceAccount; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}