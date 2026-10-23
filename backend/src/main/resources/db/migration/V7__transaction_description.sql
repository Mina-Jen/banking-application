-- Phase 4 assistant safety: a money movement may carry a short customer note.
-- A description is free text supplied by a customer, so the assistant must
-- treat it as data, never as instructions. It is never used to scope a query;
-- account access still comes only from the authenticated caller.
ALTER TABLE transactions
    ADD COLUMN description VARCHAR(255) NULL AFTER category;
