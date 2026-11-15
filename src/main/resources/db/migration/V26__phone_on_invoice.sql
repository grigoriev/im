-- Whether the PDF of an invoice shows the phone number of the seller. The XML carries it anyway, as XRechnung needs it
-- (BR-DE-6). Off by default: a freelancer may not want the number on every invoice.
ALTER TABLE seller_profile ADD COLUMN phone_on_invoice BOOLEAN NOT NULL DEFAULT FALSE;
