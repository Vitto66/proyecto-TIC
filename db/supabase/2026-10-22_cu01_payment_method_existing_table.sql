-- Ajuste para cuando la tabla payment_method ya existía en Supabase (creada antes a partir del
-- UML) y por eso 2026-10-22_cu01_payment_method.sql no la creó (CREATE TABLE IF NOT EXISTS).
-- La tabla existente tenía id, tourist_id, token, brand y last4 (como CHAR), sin vencimiento.
--
-- Solo se puede correr así con la tabla VACÍA (se agregan columnas NOT NULL).
-- No borra datos. Es seguro correrlo más de una vez.

ALTER TABLE payment_method ALTER COLUMN last4 TYPE VARCHAR(4);
ALTER TABLE payment_method ALTER COLUMN last4 SET NOT NULL;
ALTER TABLE payment_method ALTER COLUMN brand SET NOT NULL;
ALTER TABLE payment_method ADD COLUMN IF NOT EXISTS expiry_month INTEGER NOT NULL CHECK (expiry_month BETWEEN 1 AND 12);
ALTER TABLE payment_method ADD COLUMN IF NOT EXISTS expiry_year  INTEGER NOT NULL;
