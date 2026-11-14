-- The detail of a dispatch holds its recipient (200) and its note (500) in full, so the history never cuts them.
ALTER TABLE audit_event ALTER COLUMN detail TYPE VARCHAR(2000);
