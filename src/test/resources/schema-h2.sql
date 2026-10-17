DROP TABLE IF EXISTS transactions;
DROP TABLE IF EXISTS accounts;

-- account numbers use VARCHAR_IGNORECASE to mimic MySQL's default case-insensitive collation

CREATE TABLE accounts (
                          id             INT AUTO_INCREMENT PRIMARY KEY,
                          account_number VARCHAR_IGNORECASE(20) NOT NULL UNIQUE,
                          account_name   VARCHAR(100)  NOT NULL,
                          balance        DECIMAL(15,2) NOT NULL DEFAULT 0.00,
                          created_at     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transactions (
                              id                    INT AUTO_INCREMENT PRIMARY KEY,
                              transaction_reference VARCHAR(20)   NOT NULL UNIQUE,
                              account_number        VARCHAR_IGNORECASE(20) NOT NULL,
                              transaction_type      VARCHAR(10)   NOT NULL,   -- VARCHAR instead of ENUM to keep H2 simple
                              amount                DECIMAL(15,2) NOT NULL,
                              balance               DECIMAL(15,2) NOT NULL,
                              reference_account     VARCHAR_IGNORECASE(20) NULL,
                              created_at            TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
                              FOREIGN KEY (account_number)    REFERENCES accounts(account_number),
                              FOREIGN KEY (reference_account) REFERENCES accounts(account_number)
);