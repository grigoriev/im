-- The business expenses (docs/PLAN.md, section 7, "Expenses"): each with its receipt, for the Anlage EÜR in WISO
-- Steuer. The amount is what was paid, with VAT: a Kleinunternehmerin deducts no input tax. An expense counts in the
-- year of its payment. Its receipt is written once and sealed like a document. An expense never goes away: a wrong one
-- is cancelled with a reason, and stays.
CREATE TABLE expense (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paid_on       DATE          NOT NULL,
    amount        NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    category      VARCHAR(16)   NOT NULL CHECK (category IN ('MATERIAL', 'SERVICES', 'ROOM', 'TELECOM', 'SOFTWARE',
                                                             'WORK_EQUIPMENT', 'OFFICE', 'LITERATURE', 'TRAINING',
                                                             'TRAVEL', 'TRANSPORT', 'ADVERTISING', 'INSURANCE',
                                                             'ADVICE', 'BANK', 'GIFTS', 'OTHER')),
    vendor        VARCHAR(200)  NOT NULL,
    description   VARCHAR(500)  NOT NULL,
    asset         BOOLEAN       NOT NULL,
    cancelled_at  TIMESTAMP WITH TIME ZONE,
    cancel_reason VARCHAR(500),
    filename      VARCHAR(200)  NOT NULL,
    media_type    VARCHAR(32)   NOT NULL CHECK (media_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    content       BYTEA         NOT NULL,
    sha256        CHAR(64)      NOT NULL,
    size          INTEGER       NOT NULL,
    seal          CHAR(64)      NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(64)   NOT NULL,
    CHECK ((cancelled_at IS NULL) = (cancel_reason IS NULL))
);

CREATE INDEX expense_paid_on ON expense (paid_on, id);

-- No expense goes away, its receipt never changes, and a cancelled one stays as it is.
CREATE FUNCTION expense_frozen() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'expense % is kept', OLD.id;
    END IF;
    IF OLD.cancelled_at IS NOT NULL THEN
        RAISE EXCEPTION 'cancelled expense % is immutable', OLD.id;
    END IF;
    IF (NEW.id, NEW.filename, NEW.media_type, NEW.content, NEW.sha256, NEW.size, NEW.seal, NEW.created_at,
            NEW.created_by)
        IS DISTINCT FROM (OLD.id, OLD.filename, OLD.media_type, OLD.content, OLD.sha256, OLD.size, OLD.seal,
            OLD.created_at, OLD.created_by) THEN
        RAISE EXCEPTION 'the receipt of expense % is immutable', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER expense_frozen BEFORE UPDATE OR DELETE ON expense FOR EACH ROW EXECUTE FUNCTION expense_frozen();
