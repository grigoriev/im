-- The seller profile (one row) and the changed default tax notes (docs/PLAN.md, section 6).
CREATE TABLE seller_profile (
    id           INTEGER      PRIMARY KEY CHECK (id = 1),
    name         VARCHAR(200) NOT NULL,
    street       VARCHAR(200) NOT NULL,
    postcode     VARCHAR(16)  NOT NULL,
    city         VARCHAR(100) NOT NULL,
    country_code VARCHAR(2)   NOT NULL,
    contact_name VARCHAR(200),
    email        VARCHAR(254) NOT NULL,
    phone        VARCHAR(50)  NOT NULL,
    tax_number   VARCHAR(32),
    vat_id       VARCHAR(14),
    iban         VARCHAR(34)  NOT NULL,
    bic          VARCHAR(11)  NOT NULL,
    tax_status   VARCHAR(16)  NOT NULL CHECK (tax_status IN ('KLEINUNTERNEHMER', 'REGULAR')),
    CHECK (tax_number IS NOT NULL OR vat_id IS NOT NULL)
);

-- A category without a row uses the default note of the code.
CREATE TABLE tax_note (
    category VARCHAR(32)  PRIMARY KEY,
    text     VARCHAR(500) NOT NULL
);
