-- Optional lines of the letterhead and the bank account (docs/PLAN.md, section 6).
ALTER TABLE seller_profile
    ADD COLUMN tagline        VARCHAR(200),
    ADD COLUMN website        VARCHAR(200),
    ADD COLUMN bank_name      VARCHAR(100),
    ADD COLUMN account_holder VARCHAR(200);
