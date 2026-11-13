-- Baseline of the schema. The domain tables follow in milestone M1.
CREATE TABLE app_info (
    key   VARCHAR(64) PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);

INSERT INTO app_info (key, value) VALUES ('schema', 'baseline');
