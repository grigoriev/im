-- The references a customer asks to find on its invoices (docs/PLAN.md, section 6): the Geschäftspartnernummer of the
-- seller at the customer (BT-29), the Bestellnummer (BT-13) and the contract (BT-12). A draft takes the first from its
-- customer and the others from its template. Issued invoices keep their snapshot and get none.
ALTER TABLE customer ADD COLUMN seller_identifier VARCHAR(200);
ALTER TABLE invoice_template ADD COLUMN purchase_order VARCHAR(200), ADD COLUMN contract VARCHAR(200);
ALTER TABLE invoice ADD COLUMN seller_identifier VARCHAR(200), ADD COLUMN purchase_order VARCHAR(200),
    ADD COLUMN contract VARCHAR(200);

-- An issued invoice keeps its references as well (V36 lists the columns that stay).
CREATE OR REPLACE FUNCTION invoice_frozen() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'issued invoice % is immutable', OLD.number;
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = 'DRAFT' THEN
        IF NEW.status NOT IN ('DRAFT', 'ISSUED') THEN
            RAISE EXCEPTION 'invoice % cannot move from % to %', OLD.id, OLD.status, NEW.status;
        END IF;
        RETURN NEW;
    END IF;
    IF (NEW.id, NEW.number, NEW.customer_id, NEW.format, NEW.issue_date, NEW.service_from, NEW.service_to,
            NEW.payment_days, NEW.buyer_reference, NEW.intro, NEW.closing, NEW.tax_note, NEW.created_at, NEW.issued_at,
            NEW.snapshot, NEW.cancels_id, NEW.activity, NEW.seal, NEW.seller_identifier, NEW.purchase_order,
            NEW.contract)
        IS DISTINCT FROM (OLD.id, OLD.number, OLD.customer_id, OLD.format, OLD.issue_date, OLD.service_from,
            OLD.service_to, OLD.payment_days, OLD.buyer_reference, OLD.intro, OLD.closing, OLD.tax_note, OLD.created_at,
            OLD.issued_at, OLD.snapshot, OLD.cancels_id, OLD.activity, OLD.seal, OLD.seller_identifier,
            OLD.purchase_order, OLD.contract) THEN
        RAISE EXCEPTION 'issued invoice % is immutable', OLD.number;
    END IF;
    IF NEW.status <> OLD.status AND (OLD.status, NEW.status) NOT IN (('ISSUED', 'SENT'), ('ISSUED', 'PAID'),
            ('SENT', 'PAID'), ('PAID', 'SENT'), ('PAID', 'ISSUED'), ('ISSUED', 'CANCELLED'), ('SENT', 'CANCELLED'),
            ('PAID', 'CANCELLED')) THEN
        RAISE EXCEPTION 'invoice % cannot move from % to %', OLD.number, OLD.status, NEW.status;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
