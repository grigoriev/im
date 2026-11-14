-- The dispatches of an invoice (docs/PLAN.md, section 6): when, to whom, which way, by whom. Written once.
CREATE TABLE dispatch (
    id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id BIGINT       NOT NULL REFERENCES invoice (id),
    sent_on    DATE         NOT NULL,
    channel    VARCHAR(16)  NOT NULL CHECK (channel IN ('EMAIL_APP', 'OWN_MAIL', 'POST', 'PORTAL', 'IN_PERSON')),
    recipient  VARCHAR(200),
    note       VARCHAR(500),
    username   VARCHAR(64)  NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX dispatch_invoice ON dispatch (invoice_id, sent_on, id);

CREATE FUNCTION dispatch_frozen() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'dispatch % is immutable', OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER dispatch_frozen BEFORE UPDATE OR DELETE ON dispatch FOR EACH ROW EXECUTE FUNCTION dispatch_frozen();
