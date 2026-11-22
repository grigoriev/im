-- The status of an invoice moves only along its graph (docs/PLAN.md, section 6, "Invoice status"):
--   DRAFT -> ISSUED                    the issue, or the registration of an external invoice
--   ISSUED -> SENT                     the first dispatch
--   ISSUED, SENT -> PAID               the payments cover the total
--   PAID -> SENT, PAID -> ISSUED       a refund leaves money open, after a dispatch or before
--   ISSUED, SENT, PAID -> CANCELLED    the issue of its Stornorechnung
-- Nothing leaves CANCELLED, and nothing goes back to DRAFT. Besides the status, an issued invoice changes only its
-- bookkeeping (updated_at, version) and template_id, which the deletion of its template sets to NULL. Its id and
-- created_at stay too.
CREATE OR REPLACE FUNCTION invoice_frozen() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'issued invoice % is immutable', OLD.number;
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = 'DRAFT' THEN
        IF NEW.status NOT IN ('DRAFT', 'ISSUED') THEN
            RAISE EXCEPTION 'invoice % cannot move from % to %', OLD.id, OLD.status, NEW.status;
        END IF;
        RETURN NEW;
    END IF;
    IF (NEW.id, NEW.number, NEW.customer_id, NEW.format, NEW.issue_date, NEW.service_from, NEW.service_to,
            NEW.payment_days, NEW.buyer_reference, NEW.intro, NEW.closing, NEW.tax_note, NEW.created_at, NEW.issued_at,
            NEW.snapshot, NEW.cancels_id, NEW.activity, NEW.seal)
        IS DISTINCT FROM (OLD.id, OLD.number, OLD.customer_id, OLD.format, OLD.issue_date, OLD.service_from,
            OLD.service_to, OLD.payment_days, OLD.buyer_reference, OLD.intro, OLD.closing, OLD.tax_note, OLD.created_at,
            OLD.issued_at, OLD.snapshot, OLD.cancels_id, OLD.activity, OLD.seal) THEN
        RAISE EXCEPTION 'issued invoice % is immutable', OLD.number;
    END IF;
    IF NEW.status <> OLD.status AND (OLD.status, NEW.status) NOT IN (('ISSUED', 'SENT'), ('ISSUED', 'PAID'),
            ('SENT', 'PAID'), ('PAID', 'SENT'), ('PAID', 'ISSUED'), ('ISSUED', 'CANCELLED'), ('SENT', 'CANCELLED'),
            ('PAID', 'CANCELLED')) THEN
        RAISE EXCEPTION 'invoice % cannot move from % to %', OLD.number, OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- The explained gaps of a year by hand are business data too (V35): while one exists, the audit log stays, as the
-- import into an empty installation checks the same tables (BackupService.BUSINESS).
CREATE OR REPLACE FUNCTION audit_event_kept() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'the audit log is written once';
    END IF;
    IF EXISTS (SELECT 1 FROM seller_profile) OR EXISTS (SELECT 1 FROM customer)
            OR EXISTS (SELECT 1 FROM invoice_template) OR EXISTS (SELECT 1 FROM invoice)
            OR EXISTS (SELECT 1 FROM number_gap)
            OR EXISTS (SELECT 1 FROM business_document) OR EXISTS (SELECT 1 FROM expense) THEN
        RAISE EXCEPTION 'the audit log is kept: only an import into an empty installation replaces it';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;
