-- Borra la tabla vieja `payment`, que quedó del primer modelo y la aplicación no usa.
-- Los pagos y las devoluciones se guardan en `reservation_payment` desde el script
-- 2026-10-26_cu14_cu15_participants_payments.sql. Ninguna tabla tiene claves foráneas hacia `payment`.
--
-- Se aplica una sola vez en el SQL Editor de Supabase. La app arranca igual con o sin este script
-- (ninguna entidad usa la tabla).

-- 1) Comprobación: tiene que dar 0. Si da más, no sigas y avisá al equipo.
SELECT count(*) AS filas_en_payment FROM payment;

-- 2) Borrado
DROP TABLE IF EXISTS payment;
