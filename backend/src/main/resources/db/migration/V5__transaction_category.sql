-- Phase 4, Quest 9: every transaction may carry one spending category.
-- The column stays NULL until a category is suggested and accepted by the
-- account's owner, so existing history is not guessed at migration time.
-- VARCHAR rather than ENUM keeps the vocabulary in Java (TransactionCategory)
-- and lets a future category be added without rebuilding the table.
ALTER TABLE transactions
    ADD COLUMN category VARCHAR(40) NULL AFTER reference_account;
