-- The issue (docs/PLAN.md, section 6): the number, the frozen snapshot and the documents of an invoice.
ALTER TABLE invoice
    ADD COLUMN issued_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN snapshot  TEXT,
    ADD CHECK ((status = 'DRAFT') = (issued_at IS NULL)),
    ADD CHECK ((status = 'DRAFT') = (snapshot IS NULL));

-- The PDF and the XML of an issued invoice, with their SHA-256. They live in the database: the issue writes them in
-- the same transaction as the number, and pg_dump backs them up.
CREATE TABLE invoice_document (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id BIGINT       NOT NULL REFERENCES invoice (id),
    kind       VARCHAR(8)   NOT NULL CHECK (kind IN ('PDF', 'XML')),
    filename   VARCHAR(100) NOT NULL,
    content    BYTEA        NOT NULL,
    sha256     CHAR(64)     NOT NULL,
    size       INTEGER      NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (invoice_id, kind)
);

-- Issued invoices are immutable. Only the status may move on (sent, paid, cancelled), with version and updated_at.
CREATE FUNCTION invoice_frozen() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'issued invoice % is immutable', OLD.number;
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status <> 'DRAFT' AND (NEW.number, NEW.customer_id, NEW.format, NEW.issue_date, NEW.service_from,
            NEW.service_to, NEW.payment_days, NEW.buyer_reference, NEW.intro, NEW.closing, NEW.tax_note, NEW.issued_at,
            NEW.snapshot, NEW.status = 'DRAFT')
        IS DISTINCT FROM (OLD.number, OLD.customer_id, OLD.format, OLD.issue_date, OLD.service_from, OLD.service_to,
            OLD.payment_days, OLD.buyer_reference, OLD.intro, OLD.closing, OLD.tax_note, OLD.issued_at, OLD.snapshot,
            FALSE) THEN
        RAISE EXCEPTION 'issued invoice % is immutable', OLD.number;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER invoice_frozen BEFORE UPDATE OR DELETE ON invoice FOR EACH ROW EXECUTE FUNCTION invoice_frozen();

-- The lines of an issued invoice never change. A draft that is deleted takes its lines with it.
CREATE FUNCTION invoice_line_frozen() RETURNS trigger AS $$
DECLARE
    parent_status VARCHAR(16);
BEGIN
    SELECT status INTO parent_status FROM invoice WHERE id = COALESCE(NEW.invoice_id, OLD.invoice_id);
    IF parent_status IS NOT NULL AND parent_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'the lines of an issued invoice are immutable';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER invoice_line_frozen BEFORE INSERT OR UPDATE OR DELETE ON invoice_line
    FOR EACH ROW EXECUTE FUNCTION invoice_line_frozen();

-- Documents are written once.
CREATE FUNCTION invoice_document_frozen() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'the documents of an issued invoice are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER invoice_document_frozen BEFORE UPDATE OR DELETE ON invoice_document
    FOR EACH ROW EXECUTE FUNCTION invoice_document_frozen();
