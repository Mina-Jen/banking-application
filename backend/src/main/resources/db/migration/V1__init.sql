-- Initial banking schema, converted from database/banking_db.sql.
-- TRANSFER is stored as two rows: TRANSFER_OUT on the source account and
-- TRANSFER_IN on the destination account.
-- utf8mb4_general_ci keeps the migration portable between MySQL 8 and the
-- MariaDB version shipped with XAMPP (which has no utf8mb4_0900_* collations).

CREATE TABLE accounts (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    account_number VARCHAR(20)   NOT NULL UNIQUE,
    account_name   VARCHAR(100)  NOT NULL,
    balance        DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    created_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;

CREATE TABLE transactions (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    transaction_reference VARCHAR(20)   NOT NULL UNIQUE,
    account_number        VARCHAR(20)   NOT NULL,
    transaction_type      ENUM('DEPOSIT','WITHDRAW','TRANSFER_OUT','TRANSFER_IN') NOT NULL,
    amount                DECIMAL(15,2) NOT NULL,
    balance               DECIMAL(15,2) NOT NULL,   -- balance AFTER the transaction
    reference_account     VARCHAR(20)   NULL,       -- other account in a transfer
    created_at            TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_number) REFERENCES accounts (account_number),
    CONSTRAINT fk_transactions_reference_account
        FOREIGN KEY (reference_account) REFERENCES accounts (account_number)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;
