-- The §19 revenue of a year before the app existed (docs/PLAN.md, section 7): 2026 for the start in 2027.
CREATE TABLE threshold_baseline (
    year   INTEGER        PRIMARY KEY CHECK (year BETWEEN 2000 AND 9999),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0)
);
