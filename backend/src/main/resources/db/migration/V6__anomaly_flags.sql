-- Phase 4 anomaly flags: a rule found a transaction worth a human look.
-- A flag never blocks, changes or reverses the money movement; it only
-- records what the rule saw so an ADMIN can review it later. Rules run after
-- the transaction row is written, and only for outbound movements
-- (WITHDRAW, TRANSFER_OUT), because those are the ones that remove money.

CREATE TABLE anomaly_flags (
    id                    INT           AUTO_INCREMENT PRIMARY KEY,
    transaction_id        INT           NOT NULL,
    -- The transaction's own values are copied here as a snapshot: a flag is
    -- an audit record of what was seen, and an admin list needs no join.
    transaction_reference VARCHAR(20)   NOT NULL,
    account_number        VARCHAR(20)   NOT NULL,
    transaction_type      VARCHAR(20)   NOT NULL,
    amount                DECIMAL(15,2) NOT NULL,
    rule_code             VARCHAR(30)   NOT NULL,
    status                VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    details               VARCHAR(500)  NOT NULL,
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at           TIMESTAMP     NULL DEFAULT NULL,
    reviewed_by           VARCHAR(50)   NULL,
    CONSTRAINT fk_anomaly_flags_transaction
        FOREIGN KEY (transaction_id) REFERENCES transactions (id),
    -- One flag per transaction and rule, even if evaluation somehow runs twice.
    CONSTRAINT uq_anomaly_flags_transaction_rule
        UNIQUE (transaction_id, rule_code),
    INDEX idx_anomaly_flags_status_created (status, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;
