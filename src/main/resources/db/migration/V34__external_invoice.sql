-- External invoices (docs/PLAN.md, section 7, "External invoices"): made outside the app, in a form a customer demands
-- or before the app existed. The app keeps their original files and the facts typed in by hand. A customer and a
-- template may name the format, so a new draft for that customer is external from the start.
ALTER TABLE invoice DROP CONSTRAINT invoice_format_check,
    ADD CONSTRAINT invoice_format_check
        CHECK (format IN ('ZUGFERD_EN16931', 'ZUGFERD_XRECHNUNG', 'XRECHNUNG', 'EXTERNAL'));
ALTER TABLE customer DROP CONSTRAINT customer_format_check,
    ADD CONSTRAINT customer_format_check
        CHECK (format IN ('ZUGFERD_EN16931', 'ZUGFERD_XRECHNUNG', 'XRECHNUNG', 'EXTERNAL'));
ALTER TABLE invoice_template DROP CONSTRAINT invoice_template_format_check,
    ADD CONSTRAINT invoice_template_format_check
        CHECK (format IN ('ZUGFERD_EN16931', 'ZUGFERD_XRECHNUNG', 'XRECHNUNG', 'EXTERNAL'));

-- The original files of an external invoice. They live in the database, so pg_dump backs them up. Each is written
-- once and sealed; a further file is a new row.
CREATE TABLE invoice_original (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id BIGINT       NOT NULL REFERENCES invoice (id),
    filename   VARCHAR(200) NOT NULL,
    media_type VARCHAR(80)  NOT NULL CHECK (media_type IN ('application/pdf', 'application/xml',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document')),
    content    BYTEA        NOT NULL,
    sha256     CHAR(64)     NOT NULL,
    size       INTEGER      NOT NULL,
    seal       CHAR(64)     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(64)  NOT NULL
);

CREATE INDEX invoice_original_invoice ON invoice_original (invoice_id, id);

CREATE FUNCTION invoice_original_frozen() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'original file % of an invoice is immutable', OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER invoice_original_frozen BEFORE UPDATE OR DELETE ON invoice_original
    FOR EACH ROW EXECUTE FUNCTION invoice_original_frozen();
