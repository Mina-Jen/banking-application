package com.tesda.ciicc.banking.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * One rule finding about one transaction, waiting for an ADMIN to review it.
 * The transaction's values are copied as plain columns (the same no-relations
 * style as the rest of the model) so a flag stays readable without a join and
 * records what the rule saw at that moment.
 */
@Entity
@Table(name = "anomaly_flags",
        uniqueConstraints = @UniqueConstraint(columnNames = {"transaction_id", "rule_code"}))
public class AnomalyFlag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(name = "transaction_id", nullable = false)
    private int transactionId;

    @Column(name = "transaction_reference", nullable = false, length = 20)
    private String transactionReference;

    @Column(name = "account_number", nullable = false, length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_code", nullable = false, length = 30)
    private AnomalyRule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AnomalyStatus status = AnomalyStatus.OPEN;

    @Column(name = "details", nullable = false, length = 500)
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    @Column(name = "reviewed_at")
    private Timestamp reviewedAt;

    @Column(name = "reviewed_by", length = 50)
    private String reviewedBy;

    /** For JPA only. */
    protected AnomalyFlag() {
    }

    public AnomalyFlag(Transaction transaction, AnomalyRule rule, String details) {
        this.transactionId = transaction.getId();
        this.transactionReference = transaction.getTransactionReference();
        this.accountNumber = transaction.getAccountNumber();
        this.transactionType = transaction.getTransactionType();
        this.amount = transaction.getAmount();
        this.rule = rule;
        this.details = details;
        this.status = AnomalyStatus.OPEN;
    }

    /** Closes the flag. The service decides whether a review is allowed. */
    public void resolve(AnomalyStatus status, String reviewedBy, Timestamp reviewedAt) {
        this.status = status;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
    }

    public int getId() { return id; }

    public int getTransactionId() { return transactionId; }

    public String getTransactionReference() { return transactionReference; }

    public String getAccountNumber() { return accountNumber; }

    public TransactionType getTransactionType() { return transactionType; }

    public BigDecimal getAmount() { return amount; }

    public AnomalyRule getRule() { return rule; }

    public AnomalyStatus getStatus() { return status; }

    public String getDetails() { return details; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getReviewedAt() { return reviewedAt; }

    public String getReviewedBy() { return reviewedBy; }
}
