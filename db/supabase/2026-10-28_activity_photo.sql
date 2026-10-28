-- Foto de portada de las actividades (CU-08 Publicar y CU-10 Modificar): se ve en el inicio,
-- en Explorar actividades y en el detalle de cada actividad.
-- La foto puede ser la que sube el prestador (image + content_type) o la dirección de una foto
-- publicada en otro sitio (external_url, por ejemplo Wikimedia Commons), que no pesa en la base.
-- Correr en el SQL Editor de Supabase ANTES de arrancar la app con el perfil postgres.
-- No modifica ni borra datos. Se puede correr de nuevo sin problema (también si ya se había
-- corrido la primera versión de este script, que no tenía external_url).

CREATE TABLE IF NOT EXISTS activity_photo (
    activity_id  BIGINT       PRIMARY KEY REFERENCES activity (id) ON DELETE CASCADE,
    content_type VARCHAR(30),
    image        BYTEA,
    external_url VARCHAR(500),
    updated_at   TIMESTAMP    NOT NULL
);

-- Si la tabla ya existía con la primera versión: se agrega la columna y la imagen pasa a ser opcional
ALTER TABLE activity_photo ADD COLUMN IF NOT EXISTS external_url VARCHAR(500);
ALTER TABLE activity_photo ALTER COLUMN image DROP NOT NULL;
ALTER TABLE activity_photo ALTER COLUMN content_type DROP NOT NULL;
ALTER TABLE activity_photo DROP CONSTRAINT IF EXISTS activity_photo_content_type_check;

-- Cada foto es subida (con su tipo) o externa (https)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_activity_photo_source') THEN
        ALTER TABLE activity_photo ADD CONSTRAINT ck_activity_photo_source CHECK (
            (image IS NOT NULL AND content_type IN ('image/jpeg', 'image/png', 'image/webp'))
            OR external_url LIKE 'https://%');
    END IF;
END $$;

-- Igual que las demás tablas: con RLS activado, solo la app (que entra con el usuario de la base) la usa
ALTER TABLE activity_photo ENABLE ROW LEVEL SECURITY;
