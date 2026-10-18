-- CU-02 Registrarse como prestador
-- El formulario pide domicilio/dirección (ambos tipos de persona) y el nombre del
-- emprendimiento (persona jurídica). Correr una sola vez en el SQL Editor de Supabase
-- ANTES de levantar la app con el perfil "postgres" (si no, ddl-auto=validate no arranca).

ALTER TABLE provider ADD COLUMN IF NOT EXISTS address       VARCHAR(200);
ALTER TABLE provider ADD COLUMN IF NOT EXISTS business_name VARCHAR(150);

-- Si el CHECK ck_provider_person_data exige datos por tipo de persona, se puede
-- sumar el nombre del emprendimiento para las personas jurídicas. Revisar la
-- definición actual con:
--   SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_provider_person_data';
