-- Invoice templates per customer (docs/PLAN.md, section 7). A template goes with its customer.
CREATE TABLE invoice_template (
    id            BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id   BIGINT        NOT NULL REFERENCES customer (id) ON DELETE CASCADE,
    name          VARCHAR(100)  NOT NULL,
    format        VARCHAR(32)   NOT NULL CHECK (format IN ('ZUGFERD_EN16931', 'ZUGFERD_XRECHNUNG', 'XRECHNUNG')),
    payment_days  INTEGER       NOT NULL CHECK (payment_days BETWEEN 0 AND 365),
    intro         VARCHAR(2000),
    closing       VARCHAR(2000),
    tax_note      VARCHAR(1000),
    email_subject VARCHAR(200),
    email_body    VARCHAR(4000),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    version       BIGINT        NOT NULL
);

CREATE INDEX invoice_template_customer ON invoice_template (customer_id);

CREATE TABLE invoice_template_line (
    template_id  BIGINT         NOT NULL REFERENCES invoice_template (id) ON DELETE CASCADE,
    position     INTEGER        NOT NULL,
    text         VARCHAR(500)   NOT NULL,
    lump_sum     BOOLEAN        NOT NULL,
    quantity     NUMERIC(16, 4) NOT NULL,
    unit_price   NUMERIC(16, 4) NOT NULL,
    category     VARCHAR(32)    NOT NULL,
    PRIMARY KEY (template_id, position)
);
