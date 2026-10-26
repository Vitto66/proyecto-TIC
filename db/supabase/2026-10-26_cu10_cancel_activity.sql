-- CU-10 Modificar o dar de baja una actividad.
-- Al dar de baja una actividad, sus reservas futuras pasan al estado CANCELLED (y si estaban
-- pagas se devuelve el 100% del pago). Este script le agrega ese estado a la regla (CHECK) de
-- reservation.status. Correr UNA vez en el SQL Editor de Supabase, ANTES de arrancar la app.
-- No borra ni modifica datos: solo cambia qué estados acepta la columna. Se puede correr de nuevo.

-- 1. Sacar la regla vieja de estados de reservation (no sabemos con qué nombre se creó)
DO $$
DECLARE
    regla record;
BEGIN
    FOR regla IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'reservation'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE reservation DROP CONSTRAINT %I', regla.conname);
    END LOOP;
END $$;

-- 2. Poner la regla nueva, con CANCELLED
ALTER TABLE reservation
    ADD CONSTRAINT reservation_status_check
    CHECK (status IN ('PENDING', 'CONFIRMED', 'FINISHED', 'EXPIRED', 'CANCELLED'));

-- 3. Para comprobar: tiene que mostrar la regla con los 5 estados
SELECT conname, pg_get_constraintdef(oid) AS regla
FROM pg_constraint
WHERE conrelid = 'reservation'::regclass AND contype = 'c';
