-- Reference data: supported currencies (ISO 4217) and their number of decimal places.
CREATE TABLE currencies (
    code        CHAR(3)     PRIMARY KEY CHECK (code ~ '^[A-Z]{3}$'),
    name        VARCHAR(64) NOT NULL,
    minor_units SMALLINT    NOT NULL CHECK (minor_units BETWEEN 0 AND 4)
);

INSERT INTO currencies (code, name, minor_units) VALUES
    ('EUR', 'Euro', 2),
    ('TND', 'Tunisian dinar', 3);
