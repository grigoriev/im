-- A Stornorechnung names the invoice it cancels (docs/PLAN.md, section 6). One invoice has at most one, a draft
-- included. The link is part of the issued invoice and freezes with it.
ALTER TABLE invoice ADD COLUMN cancels_id BIGINT REFERENCES invoice (id);

CREATE UNIQUE INDEX invoice_cancels ON invoice (cancels_id) WHERE cancels_id IS NOT NULL;

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
            NEW.snapshot, NEW.cancels_id, NEW.status = 'DRAFT')
        IS DISTINCT FROM (OLD.number, OLD.customer_id, OLD.format, OLD.issue_date, OLD.service_from, OLD.service_to,
            OLD.payment_days, OLD.buyer_reference, OLD.intro, OLD.closing, OLD.tax_note, OLD.issued_at, OLD.snapshot,
            OLD.cancels_id, FALSE) THEN
        RAISE EXCEPTION 'issued invoice % is immutable', OLD.number;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
