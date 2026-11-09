-- Cancelación por el turista y adulto responsable que no participa (09/11/2026).
-- Correr UNA vez en el SQL Editor de Supabase, ANTES de arrancar la app con el perfil postgres.
-- Agrega columnas con valores por defecto y afloja un CHECK: no cambia ni borra datos. Se puede correr de nuevo sin problema.

-- La política de cancelación de cada actividad: si se permite y hasta cuántas horas antes del inicio.
-- Las actividades que ya existen quedan sin permitir cancelación (como hasta ahora).
ALTER TABLE activity ADD COLUMN IF NOT EXISTS cancellation_allowed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE activity ADD COLUMN IF NOT EXISTS cancellation_hours INTEGER NOT NULL DEFAULT 24;
ALTER TABLE activity DROP CONSTRAINT IF EXISTS activity_cancellation_hours_check;
ALTER TABLE activity ADD CONSTRAINT activity_cancellation_hours_check CHECK (cancellation_hours BETWEEN 0 AND 720);

-- Si el turista que reserva participa (si no, es solo el adulto responsable de las personas que anota)
-- y si la cancelación la hizo él.
ALTER TABLE reservation ADD COLUMN IF NOT EXISTS tourist_attends BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE reservation ADD COLUMN IF NOT EXISTS cancelled_by_tourist BOOLEAN NOT NULL DEFAULT FALSE;

-- Si el turista es solo el adulto responsable, los que van se numeran desde la persona 1.
ALTER TABLE reservation_participant DROP CONSTRAINT IF EXISTS reservation_participant_person_number_check;
ALTER TABLE reservation_participant ADD CONSTRAINT reservation_participant_person_number_check CHECK (person_number >= 1);
