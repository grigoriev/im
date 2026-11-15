-- The payment reminders of an invoice (docs/PLAN.md, section 7): sent from the app or noted by hand. Written once,
-- so the history of reminders stays complete.
CREATE TABLE reminder (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_id  BIGINT       NOT NULL REFERENCES invoice (id),
    reminded_on DATE         NOT NULL,
    channel     VARCHAR(16)  NOT NULL CHECK (channel IN ('EMAIL_APP', 'MANUAL')),
    recipient   VARCHAR(254),
    note        VARCHAR(500),
    username    VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX reminder_invoice ON reminder (invoice_id, reminded_on, id);

CREATE FUNCTION reminder_frozen() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'reminder % is immutable', OLD.id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER reminder_frozen BEFORE UPDATE OR DELETE ON reminder FOR EACH ROW EXECUTE FUNCTION reminder_frozen();
