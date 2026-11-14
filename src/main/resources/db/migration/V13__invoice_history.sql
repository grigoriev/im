-- The history of an invoice (docs/PLAN.md, section 7): its entries of the audit log.
-- No foreign key: a deleted draft keeps its entries, and IDs never repeat.
ALTER TABLE audit_event ADD COLUMN invoice_id BIGINT;

CREATE INDEX audit_event_invoice ON audit_event (invoice_id, at, id) WHERE invoice_id IS NOT NULL;
