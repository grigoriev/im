-- The numbers of a year before the app (docs/PLAN.md, section 7, "External invoices"): the invoices of that year come
-- in as external invoices with the number of their file, in any order. A year by hand takes no number of the counter.
-- "Jahr abschließen" ends it: the counter continues after the highest number, and the year stays closed.
ALTER TABLE number_sequence
    ADD COLUMN manual BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN closed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT number_sequence_manual_unused CHECK (NOT (manual AND used)),
    ADD CONSTRAINT number_sequence_manual_open CHECK (NOT (manual AND closed));

-- A gap in the numbers of a year by hand, with its reason, for example a spoiled number. Written once.
CREATE TABLE number_gap (
    year       INTEGER      NOT NULL REFERENCES number_sequence (year),
    counter    INTEGER      NOT NULL CHECK (counter >= 1),
    reason     VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(64)  NOT NULL,
    PRIMARY KEY (year, counter)
);

CREATE FUNCTION number_gap_kept() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'the reason of gap %-% is kept', OLD.year, OLD.counter;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER number_gap_kept BEFORE UPDATE OR DELETE ON number_gap
    FOR EACH ROW EXECUTE FUNCTION number_gap_kept();
