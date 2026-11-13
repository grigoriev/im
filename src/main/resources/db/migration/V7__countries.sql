-- The short list of countries that the forms offer. The admin changes it; every ISO country stays valid.
CREATE TABLE country (
    code VARCHAR(2) PRIMARY KEY
);

-- The EU, and the neighbors and markets a German freelancer bills most often.
INSERT INTO country (code) VALUES
    ('AT'), ('BE'), ('BG'), ('CY'), ('CZ'), ('DE'), ('DK'), ('EE'), ('ES'), ('FI'), ('FR'), ('GR'), ('HR'), ('HU'),
    ('IE'), ('IT'), ('LT'), ('LU'), ('LV'), ('MT'), ('NL'), ('PL'), ('PT'), ('RO'), ('SE'), ('SI'), ('SK'),
    ('CH'), ('GB'), ('LI'), ('NO'), ('US');
