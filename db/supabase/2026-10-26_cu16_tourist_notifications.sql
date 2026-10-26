-- CU-16: avisos al turista (por ejemplo, cuando una reserva vence porque no se pagó a tiempo).
-- Correr UNA vez en el SQL Editor de Supabase, ANTES de arrancar la app con el perfil postgres.
-- Solo crea una tabla nueva: no modifica ni borra datos existentes. Se puede correr de nuevo sin problema.

CREATE TABLE IF NOT EXISTS tourist_notification (
    id             BIGSERIAL PRIMARY KEY,
    tourist_id     BIGINT       NOT NULL REFERENCES tourist (user_id) ON DELETE CASCADE,
    reservation_id BIGINT       REFERENCES reservation (id) ON DELETE CASCADE,
    message        VARCHAR(300) NOT NULL,
    created_at     TIMESTAMP    NOT NULL,
    read_at        TIMESTAMP
);
-- Lo que más se consulta: los avisos sin leer de un turista
CREATE INDEX IF NOT EXISTS idx_tourist_notification_unread ON tourist_notification (tourist_id, read_at);

-- Igual que las demás tablas: con RLS activado, solo la app (que entra con el usuario de la base) la usa
ALTER TABLE tourist_notification ENABLE ROW LEVEL SECURITY;
