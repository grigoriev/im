-- V29 sealed every document and issued invoice. From now on each one carries its seal.
ALTER TABLE invoice_document ALTER COLUMN seal SET NOT NULL;
ALTER TABLE business_document ALTER COLUMN seal SET NOT NULL;
ALTER TABLE invoice ADD CHECK ((status = 'DRAFT') = (seal IS NULL));
