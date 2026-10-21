-- =====================================================================
-- 001_split_transfer.sql
-- Splits transaction_type 'TRANSFER' into 'TRANSFER_OUT' (source account)
-- and 'TRANSFER_IN' (destination account) for EXISTING data.
--
-- BEFORE RUNNING: back up the table, e.g.
--   mysqldump -u root banking_db transactions > transactions_backup.sql
-- ALTER TABLE commits immediately in MySQL/MariaDB, so this script cannot
-- be rolled back as one transaction. The backup is your undo.
--
-- Pairing rule: both rows of a transfer have the same created_at and
-- mirrored account_number / reference_account. The lower id is the source
-- (TRANSFER_OUT), the higher id is the destination (TRANSFER_IN).
--
-- created_at only has whole-second precision, so two transfers between
-- the same accounts in the same second could match each other's rows.
-- To stay safe, each row is paired only with its NEAREST mirror row, and
-- only when that choice is mutual and no row ends up in two pairs.
-- Anything ambiguous is left as 'TRANSFER' and reported in step 3.
-- =====================================================================

USE banking_db;

-- Strict mode for this session only: if any 'TRANSFER' row is left over,
-- step 4 FAILS with an error instead of silently turning it into ''.
SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ENGINE_SUBSTITUTION';

-- ---------------------------------------------------------------------
-- Step 1: widen the ENUM so old and new values can exist together.
-- ---------------------------------------------------------------------
ALTER TABLE transactions
    MODIFY transaction_type
        ENUM('DEPOSIT','WITHDRAW','TRANSFER','TRANSFER_OUT','TRANSFER_IN') NOT NULL;

-- ---------------------------------------------------------------------
-- Step 2: work out the pairs and convert them.
-- (Helper tables are plain tables, not TEMPORARY, because MySQL cannot
--  reference one TEMPORARY table twice in the same query.)
-- ---------------------------------------------------------------------

-- 2a. Every possible (source, destination) match.
DROP TABLE IF EXISTS migration_001_candidates;
CREATE TABLE migration_001_candidates AS
SELECT o.id AS out_id, i.id AS in_id
FROM transactions o
JOIN transactions i
  ON  i.created_at        = o.created_at
  AND i.account_number    = o.reference_account
  AND i.reference_account = o.account_number
  AND i.id > o.id
WHERE o.transaction_type = 'TRANSFER'
  AND i.transaction_type = 'TRANSFER';

-- 2b. Keep a match only if each row is the other's nearest mirror.
DROP TABLE IF EXISTS migration_001_pairs;
CREATE TABLE migration_001_pairs AS
SELECT c.out_id, c.in_id
FROM migration_001_candidates c
WHERE c.in_id  = (SELECT MIN(c2.in_id)  FROM migration_001_candidates c2 WHERE c2.out_id = c.out_id)
  AND c.out_id = (SELECT MAX(c3.out_id) FROM migration_001_candidates c3 WHERE c3.in_id  = c.in_id);

-- 2c. Any row that still appears in more than one pair is ambiguous.
DROP TABLE IF EXISTS migration_001_conflicts;
CREATE TABLE migration_001_conflicts AS
SELECT id
FROM (SELECT out_id AS id FROM migration_001_pairs
      UNION ALL
      SELECT in_id  AS id FROM migration_001_pairs) ids
GROUP BY id
HAVING COUNT(*) > 1;

-- 2d. Convert the unambiguous pairs (one statement, so it is atomic).
UPDATE transactions o
JOIN migration_001_pairs p          ON p.out_id = o.id
JOIN transactions i                 ON i.id     = p.in_id
LEFT JOIN migration_001_conflicts x1 ON x1.id   = p.out_id
LEFT JOIN migration_001_conflicts x2 ON x2.id   = p.in_id
SET o.transaction_type = 'TRANSFER_OUT',
    i.transaction_type = 'TRANSFER_IN'
WHERE x1.id IS NULL
  AND x2.id IS NULL;

DROP TABLE migration_001_conflicts;
DROP TABLE migration_001_pairs;
DROP TABLE migration_001_candidates;

-- ---------------------------------------------------------------------
-- Step 3: report anything NOT converted. This should return ZERO rows.
-- If it returns rows, step 4 will fail (on purpose). Set those rows to
-- TRANSFER_OUT / TRANSFER_IN by hand, then run step 4 on its own.
-- ---------------------------------------------------------------------
SELECT id, transaction_reference, account_number, reference_account, amount, created_at
FROM transactions
WHERE transaction_type = 'TRANSFER'
ORDER BY id;

-- ---------------------------------------------------------------------
-- Step 4: remove 'TRANSFER' from the ENUM.
-- ---------------------------------------------------------------------
ALTER TABLE transactions
    MODIFY transaction_type
        ENUM('DEPOSIT','WITHDRAW','TRANSFER_OUT','TRANSFER_IN') NOT NULL;
