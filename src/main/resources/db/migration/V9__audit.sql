-- The audit log (docs/PLAN.md, section 6): entries are added, never changed.
CREATE TABLE audit_event (
    id       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    at       TIMESTAMP WITH TIME ZONE NOT NULL,
    username VARCHAR(64)  NOT NULL,
    action   VARCHAR(32)  NOT NULL,
    detail   VARCHAR(500) NOT NULL
);

CREATE INDEX audit_event_at ON audit_event (at DESC, id DESC);
