CREATE TABLE IF NOT EXISTS role
(
    id   SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO role (id, name)
VALUES (1, 'ADMIN'),
       (2, 'PARAMEDIC'),
       (3, 'CLIENT')