-- The short list of countries that the forms offer. The admin changes it; every ISO country stays valid.
CREATE TABLE country (
    code VARCHAR(2) PRIMARY KEY
);

-- Only Germany at the start: the admin adds the countries of the customers.
INSERT INTO country (code) VALUES ('DE');
