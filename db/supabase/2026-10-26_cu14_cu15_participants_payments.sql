-- CU-14 y CU-15: datos de los acompañantes de una reserva y pagos con el servicio externo.
-- Correr UNA vez en el SQL Editor de Supabase, ANTES de arrancar la app con el perfil postgres.
-- Solo crea dos tablas nuevas: no modifica ni borra datos existentes. Se puede correr de nuevo sin problema.

-- Personas 2 en adelante de cada reserva (la 1 es el turista que reserva)
CREATE TABLE IF NOT EXISTS reservation_participant (
    id             BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT       NOT NULL REFERENCES reservation (id) ON DELETE CASCADE,
    person_number  INTEGER      NOT NULL CHECK (person_number >= 2),
    first_name     VARCHAR(100) NOT NULL,
    last_name      VARCHAR(100) NOT NULL,
    national_id    VARCHAR(8)   NOT NULL CHECK (national_id ~ '^[0-9]{7,8}$'),
    phone          VARCHAR(30)  NOT NULL,
    UNIQUE (reservation_id, person_number)
);
CREATE INDEX IF NOT EXISTS idx_reservation_participant_reservation ON reservation_participant (reservation_id);

-- Cada intento de pago (aprobado, rechazado, en revisión, sin respuesta o devuelto)
CREATE TABLE IF NOT EXISTS reservation_payment (
    id             BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT         NOT NULL REFERENCES reservation (id) ON DELETE CASCADE,
    provider       VARCHAR(20)    NOT NULL,
    transaction_id VARCHAR(64),
    status         VARCHAR(20)    NOT NULL
        CHECK (status IN ('APPROVED', 'REJECTED', 'IN_PROCESS', 'UNAVAILABLE', 'REFUNDED')),
    status_detail  VARCHAR(100),
    amount         NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    created_at     TIMESTAMP      NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_reservation_payment_reservation ON reservation_payment (reservation_id);

-- Igual que las demás tablas: con RLS activado, solo la app (que entra con el usuario de la base) las usa
ALTER TABLE reservation_participant ENABLE ROW LEVEL SECURITY;
ALTER TABLE reservation_payment ENABLE ROW LEVEL SECURITY;
