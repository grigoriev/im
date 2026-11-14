-- The last successful nightly backup and monthly restore test (docs/PLAN.md, section 9).
-- The scripts on the host write it, the admin page shows it. A failure sends an e-mail instead.
CREATE TABLE backup_run (
    kind         VARCHAR(16)  PRIMARY KEY CHECK (kind IN ('BACKUP', 'RESTORE_TEST')),
    succeeded_at TIMESTAMPTZ  NOT NULL,
    detail       VARCHAR(500) NOT NULL DEFAULT ''
);
