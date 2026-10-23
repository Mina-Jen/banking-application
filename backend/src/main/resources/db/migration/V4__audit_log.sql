-- Append-only trail of security-relevant events: logins (success and
-- failure), rate-limited login attempts, registrations, token refreshes,
-- denied access attempts, and which user moved money in which account.
-- Nothing updates or deletes rows here; investigating an incident means
-- reading history, not editing it.
CREATE TABLE audit_log (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    occurred_at    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    username       VARCHAR(50) NULL,        -- NULL when the caller was not (yet) known
    action         VARCHAR(40) NOT NULL,    -- LOGIN_SUCCESS / LOGIN_FAILURE / LOGIN_RATE_LIMITED /
                                            -- REGISTER / REFRESH / ACCESS_DENIED /
                                            -- ACCOUNT_CREATED / DEPOSIT / WITHDRAW / TRANSFER
    account_number VARCHAR(20) NULL,
    details        VARCHAR(500) NULL,
    ip_address     VARCHAR(45) NULL,        -- long enough for a full IPv6 address
    INDEX idx_audit_log_username (username),
    INDEX idx_audit_log_action (action)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;
