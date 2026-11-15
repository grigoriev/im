-- The seal of each document and snapshot: an HMAC-SHA256 with a key outside the database (IM_INTEGRITY_KEY). Whoever
-- changes a file and its SHA-256 in the database cannot write a seal that matches. V29 seals what exists, V30 makes
-- the seal required.
ALTER TABLE invoice_document ADD COLUMN seal CHAR(64);
ALTER TABLE business_document ADD COLUMN seal CHAR(64);
ALTER TABLE invoice ADD COLUMN seal CHAR(64);

-- The seal of an issued invoice is part of what never changes.
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
            NEW.snapshot, NEW.cancels_id, NEW.activity, NEW.seal, NEW.status = 'DRAFT')
        IS DISTINCT FROM (OLD.number, OLD.customer_id, OLD.format, OLD.issue_date, OLD.service_from, OLD.service_to,
            OLD.payment_days, OLD.buyer_reference, OLD.intro, OLD.closing, OLD.tax_note, OLD.issued_at, OLD.snapshot,
            OLD.cancels_id, OLD.activity, OLD.seal, FALSE) THEN
        RAISE EXCEPTION 'issued invoice % is immutable', OLD.number;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- The seal of a business document belongs to its file.
CREATE OR REPLACE FUNCTION business_document_frozen() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'business document % is kept', OLD.id;
    END IF;
    IF (NEW.id, NEW.replaces_id, NEW.filename, NEW.media_type, NEW.content, NEW.sha256, NEW.size, NEW.created_at,
            NEW.created_by, NEW.seal)
        IS DISTINCT FROM (OLD.id, OLD.replaces_id, OLD.filename, OLD.media_type, OLD.content, OLD.sha256, OLD.size,
            OLD.created_at, OLD.created_by, OLD.seal) THEN
        RAISE EXCEPTION 'the file of business document % is immutable', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
