-- A department or other addition below the name in the address of a customer (docs/PLAN.md, section 6). Invoices
-- keep their snapshot of the buyer, so issued invoices stay as they are.
ALTER TABLE customer ADD COLUMN address_addition VARCHAR(200);
