package com.tesda.ciicc.banking.backend.model;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.sql.Timestamp;

/**
 * One login of the REST API. The password itself is never stored, only its
 * BCrypt hash; {@code enabled = false} freezes a login without deleting it.
 * Usernames are stored lowercase, so login lookups behave the same on MySQL
 * (case-insensitive collation) and H2 (case-sensitive, used in tests).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    // The schema uses a native ENUM. Mapping it as a string keeps the Java
    // names in the column, same approach as Transaction.transactionType.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "enum('CUSTOMER','ADMIN')")
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Timestamp createdAt;

    /** For JPA only. */
    protected User() {
    }

    public User(String username, String passwordHash, Role role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public int getId() { return id; }

    public String getUsername() { return username; }

    public String getPasswordHash() { return passwordHash; }

    public Role getRole() { return role; }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Timestamp getCreatedAt() { return createdAt; }
}
