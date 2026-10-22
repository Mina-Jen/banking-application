-- One row per Idempotency-Key used by the REST API's money-moving endpoints
-- (deposit, withdraw, transfer), so a retried request cannot move money twice.
-- The claim row is INSERTed in the same transaction as the operation: a
-- concurrent duplicate blocks on the unique index and then reads the committed
-- response, while a failed operation rolls the claim back so the key can be
-- retried. That is why the response columns are filled in after the INSERT and
-- must allow NULL mid-transaction.
CREATE TABLE idempotency_keys (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    operation       VARCHAR(20)  NOT NULL,   -- DEPOSIT / WITHDRAW / TRANSFER
    request_hash    VARCHAR(64)  NOT NULL,   -- SHA-256 of operation + scope + request body
    response_status INT          NULL,       -- HTTP status stored for replay
    response_body   TEXT         NULL,       -- JSON response returned on replay
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;
