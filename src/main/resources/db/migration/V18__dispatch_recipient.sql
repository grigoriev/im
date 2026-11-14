-- A recipient holds a whole e-mail address: up to 254 characters, as the address check allows.
ALTER TABLE dispatch ALTER COLUMN recipient TYPE VARCHAR(254);
