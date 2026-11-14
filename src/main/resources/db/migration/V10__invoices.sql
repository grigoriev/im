-- Invoices and their lines (docs/PLAN.md, section 6). A draft has no number; the issue (M2d) sets it.
CREATE TABLE invoice (
    id              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    status          VARCHAR(16)   NOT NULL CHECK (status IN ('DRAFT', 'ISSUED', 'SENT', 'PAID', 'CANCELLED')),
    customer_id     BIGINT        NOT NULL REFERENCES customer (id),
    number          VARCHAR(40)   UNIQUE,
    format          VARCHAR(32)   NOT NULL CHECK (format IN ('ZUGFERD_EN16931', 'ZUGFERD_XRECHNUNG', 'XRECHNUNG')),
    issue_date      DATE          NOT NULL,
    service_from    DATE          NOT NULL,
    service_to      DATE          NOT NULL,
    payment_days    INTEGER       NOT NULL CHECK (payment_days BETWEEN 0 AND 365),
    buyer_reference VARCHAR(200),
    intro           VARCHAR(2000),
    closing         VARCHAR(2000),
    tax_note        VARCHAR(1000),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK ((status = 'DRAFT') = (number IS NULL))
);

CREATE INDEX invoice_customer ON invoice (customer_id);

CREATE TABLE invoice_line (
    invoice_id   BIGINT         NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    position     INTEGER        NOT NULL,
    text         VARCHAR(500)   NOT NULL,
    lump_sum     BOOLEAN        NOT NULL,
    quantity     NUMERIC(16, 4) NOT NULL,
    unit_price   NUMERIC(16, 4) NOT NULL,
    category     VARCHAR(32)    NOT NULL,
    PRIMARY KEY (invoice_id, position)
);
