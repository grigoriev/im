-- The business of an invoice when teaching has a tax number of its own (docs/PLAN.md, section 6). A customer suggests
-- it, a template overrides it, the draft keeps it, and the issue freezes it. NULL in a draft follows the lines:
-- teaching only is TEACHING. An issued invoice always has one; there are no older invoices to spare.
ALTER TABLE customer ADD COLUMN activity VARCHAR(8) CHECK (activity IN ('TEACHING', 'OTHER'));
ALTER TABLE invoice ADD COLUMN activity VARCHAR(8) CHECK (activity IN ('TEACHING', 'OTHER')),
    ADD CONSTRAINT invoice_issued_activity CHECK (status = 'DRAFT' OR activity IS NOT NULL);
-- A template names the business of its drafts, ahead of the customer's.
ALTER TABLE invoice_template ADD COLUMN activity VARCHAR(8) CHECK (activity IN ('TEACHING', 'OTHER'));

CREATE OR REPLACE FUNCTION invoice_frozen() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'issued invoice % is immutable', OLD.number;
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status <> 'DRAFT' AND (NEW.number, NEW.customer_id, NEW.format, NEW.issue_date, NEW.service_from,
            NEW.service_to, NEW.payment_days, NEW.buyer_reference, NEW.intro, NEW.closing, NEW.tax_note, NEW.issued_at,
            NEW.snapshot, NEW.cancels_id, NEW.activity, NEW.status = 'DRAFT')
        IS DISTINCT FROM (OLD.number, OLD.customer_id, OLD.format, OLD.issue_date, OLD.service_from, OLD.service_to,
            OLD.payment_days, OLD.buyer_reference, OLD.intro, OLD.closing, OLD.tax_note, OLD.issued_at, OLD.snapshot,
            OLD.cancels_id, OLD.activity, FALSE) THEN
        RAISE EXCEPTION 'issued invoice % is immutable', OLD.number;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
