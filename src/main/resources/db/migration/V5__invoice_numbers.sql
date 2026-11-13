-- Settings as key and value, for example the pattern of the invoice numbers.
CREATE TABLE app_setting (
    key   VARCHAR(64)  PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);

-- The counter of the invoice numbers per year (docs/PLAN.md, section 7). "used" locks the start value.
CREATE TABLE number_sequence (
    year        INTEGER PRIMARY KEY CHECK (year BETWEEN 2000 AND 9999),
    next_number INTEGER NOT NULL CHECK (next_number >= 1),
    used        BOOLEAN NOT NULL DEFAULT FALSE
);
