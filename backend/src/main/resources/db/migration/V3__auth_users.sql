-- Phase 2 authentication: application users, and the link between an account
-- and the customer who owns it. The service layer reads account_owners to
-- decide "may this caller touch this account"; ADMIN bypasses that check.
-- Same utf8mb4_general_ci choice as V1: portable between MySQL 8 and the
-- MariaDB version shipped with XAMPP.

CREATE TABLE users (
    id            INT          AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,   -- BCrypt, always 60 characters
    role          ENUM('CUSTOMER','ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;

-- One owner per account: account_number is UNIQUE, so "is this account mine?"
-- is a single lookup on this table. Accounts created by an ADMIN have no row
-- here and can only be accessed by ADMIN.
CREATE TABLE account_owners (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT         NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_account_owners_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_account_owners_account
        FOREIGN KEY (account_number) REFERENCES accounts (account_number)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;
