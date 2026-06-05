-- DATOS INICIALES

-- Se ejecuta al arrancar si spring.jpa.hibernate.ddl-auto contiene "create"
-- Inserta los usuarios iniciales

INSERT INTO IWUSER
(id, enabled, total_points, avatar,
 roles, username, password, email)
VALUES
(1, TRUE, 0, 'default-pic.png',
 'ADMIN,USER', 'a',
 '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W',
 'admin@example.com');

INSERT INTO IWUSER
(id, enabled, total_points, avatar,
 roles, username, password, email)
VALUES
(2, TRUE, 0, 'default-pic.png',
 'USER', 'b',
 '{bcrypt}$2a$10$2BpNTbrsarbHjNsUWgzfNubJqBRf.0Vz9924nRSHBqlbPKerkgX.W',
 'user@example.com');

ALTER SEQUENCE "PUBLIC"."GEN" RESTART WITH 1024;