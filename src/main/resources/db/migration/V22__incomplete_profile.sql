-- The profile saves while incomplete; the issue of an invoice checks that it is complete (docs/PLAN.md, section 6).
ALTER TABLE seller_profile
    ALTER COLUMN name DROP NOT NULL,
    ALTER COLUMN street DROP NOT NULL,
    ALTER COLUMN postcode DROP NOT NULL,
    ALTER COLUMN city DROP NOT NULL,
    ALTER COLUMN country_code DROP NOT NULL,
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN phone DROP NOT NULL,
    ALTER COLUMN iban DROP NOT NULL,
    ALTER COLUMN bic DROP NOT NULL,
    ALTER COLUMN tax_status DROP NOT NULL,
    DROP CONSTRAINT seller_profile_check;
