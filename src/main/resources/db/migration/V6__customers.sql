-- The customers (docs/PLAN.md, section 6). Invoices keep a snapshot of the buyer, so a change here leaves them as they are.
CREATE TABLE customer (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    street          VARCHAR(200) NOT NULL,
    postcode        VARCHAR(16)  NOT NULL,
    city            VARCHAR(100) NOT NULL,
    country_code    VARCHAR(2)   NOT NULL,
    contact_name    VARCHAR(200),
    email           VARCHAR(254),
    vat_id          VARCHAR(14),
    buyer_reference VARCHAR(200),
    format          VARCHAR(32)  NOT NULL CHECK (format IN ('ZUGFERD_EN16931', 'ZUGFERD_XRECHNUNG', 'XRECHNUNG'))
);
