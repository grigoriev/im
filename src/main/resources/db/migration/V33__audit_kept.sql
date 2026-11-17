-- The audit log is written once (docs/PLAN.md, section 6). Not even the app changes an entry or deletes one, so who
-- gets into the app cannot hide the own traces. Only the import into an empty installation replaces the entries of
-- the setup, such as the first admin: no business data exists then.
CREATE FUNCTION audit_event_kept() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'the audit log is written once';
    END IF;
    IF EXISTS (SELECT 1 FROM seller_profile) OR EXISTS (SELECT 1 FROM customer)
            OR EXISTS (SELECT 1 FROM invoice_template) OR EXISTS (SELECT 1 FROM invoice)
            OR EXISTS (SELECT 1 FROM business_document) OR EXISTS (SELECT 1 FROM expense) THEN
        RAISE EXCEPTION 'the audit log is kept: only an import into an empty installation replaces it';
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_event_kept BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH STATEMENT EXECUTE FUNCTION audit_event_kept();
