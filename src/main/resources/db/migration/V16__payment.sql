-- The payments of an invoice (docs/PLAN.md, section 6). A negative amount is a refund. Written once: a wrong entry is
-- corrected by a counter-entry, so the income of a closed year never changes.
CREATE TABLE payment (
    id         BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id BIGINT        NOT NULL REFERENCES invoice (id),
    paid_on    DATE          NOT NULL,
    amount     NUMERIC(12, 2) NOT NULL CHECK (amount <> 0),
    note       VARCHAR(500),
    username   VARCHAR(64)   NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX payment_invoice ON payment (invoice_id, paid_on, id);
CREATE INDEX payment_paid_on ON payment (paid_on);

CREATE FUNCTION payment_frozen() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'payment % is immutable', OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_frozen BEFORE UPDATE OR DELETE ON payment FOR EACH ROW EXECUTE FUNCTION payment_frozen();
