-- The business documents (docs/PLAN.md, section 7, "Documents"): the Verfahrensdokumentation, tax assessments,
-- letters of the tax office, certificates and contracts. They live in the database, so pg_dump backs them up.
-- A file is written once. A new version is a new row that names the one it replaces; the old one stays.
CREATE TABLE business_document (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    category      VARCHAR(16)   NOT NULL CHECK (category IN ('PROCEDURE', 'TAX_ASSESSMENT', 'TAX_OFFICE', 'CERTIFICATE',
                                                             'CONTRACT', 'OTHER')),
    title         VARCHAR(200)  NOT NULL,
    document_date DATE          NOT NULL,
    year          SMALLINT      CHECK (year BETWEEN 2000 AND 2100),
    note          VARCHAR(2000) NOT NULL,
    replaces_id   BIGINT        UNIQUE REFERENCES business_document (id),
    filename      VARCHAR(200)  NOT NULL,
    media_type    VARCHAR(32)   NOT NULL CHECK (media_type IN ('application/pdf', 'image/jpeg', 'image/png')),
    content       BYTEA         NOT NULL,
    sha256        CHAR(64)      NOT NULL,
    size          INTEGER       NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by    VARCHAR(64)   NOT NULL
);

-- No document goes away. Only its description may change: category, title, date, year and note.
CREATE FUNCTION business_document_frozen() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'business document % is kept', OLD.id;
    END IF;
    IF (NEW.id, NEW.replaces_id, NEW.filename, NEW.media_type, NEW.content, NEW.sha256, NEW.size, NEW.created_at,
            NEW.created_by)
        IS DISTINCT FROM (OLD.id, OLD.replaces_id, OLD.filename, OLD.media_type, OLD.content, OLD.sha256, OLD.size,
            OLD.created_at, OLD.created_by) THEN
        RAISE EXCEPTION 'the file of business document % is immutable', OLD.id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER business_document_frozen BEFORE UPDATE OR DELETE ON business_document
    FOR EACH ROW EXECUTE FUNCTION business_document_frozen();
