-- Valoraciones de ejemplo: 12 turistas que ya hicieron algunas de las actividades de ejemplo y
-- las valoraron (estrellas y comentario). Así las tarjetas muestran calificaciones, Explorar
-- puede ordenar por las mejor valoradas y "Lo más reservado este mes" tiene datos.
--
-- Cómo se arma cada valoración, igual que en la app (CU-17): una fecha que ya pasó → una reserva
-- FINALIZADA de ese turista → la valoración de esa reserva.
--
-- * Requiere haber corrido antes 2026-10-28_demo_activities.sql (usa sus 15 actividades).
-- * Las fechas se calculan desde HOY (entre 3 y 40 días atrás).
-- * Es seguro correrlo más de una vez: si un turista ya valoró esa actividad, se saltea. Solo agrega
--   filas nuevas (turistas, fechas pasadas, reservas y valoraciones): no modifica ni borra nada.
-- * Pesa muy poco: 44 valoraciones de texto.
--
-- Los turistas de ejemplo pueden iniciar sesión con su correo y la contraseña Demo2026
-- (por ejemplo camila.gonzalez@demo.uy) y ver sus reservas y valoraciones en "Mi espacio".

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

-- ---------- Funciones de ayuda (temporales: desaparecen al terminar) ----------

-- Un turista de ejemplo. Si el correo ya existe, se usa ese.
CREATE OR REPLACE FUNCTION pg_temp.demo_tourist(p_email TEXT, p_first TEXT, p_last TEXT, p_national_id TEXT,
                                                p_phone TEXT) RETURNS BIGINT AS $$
DECLARE
    v_user BIGINT;
BEGIN
    SELECT id INTO v_user FROM app_user WHERE lower(email) = lower(p_email);
    IF v_user IS NOT NULL THEN
        RETURN v_user;
    END IF;
    INSERT INTO app_user (email, password_hash, phone)
    VALUES (p_email, extensions.crypt('Demo2026', extensions.gen_salt('bf', 10)), p_phone)
    RETURNING id INTO v_user;
    INSERT INTO tourist (user_id, first_name, last_name, national_id)
    VALUES (v_user, p_first, p_last, p_national_id);
    RETURN v_user;
END $$ LANGUAGE plpgsql;

-- Una valoración: el turista hizo la actividad hace p_days_ago días (con p_people personas) y le dio
-- p_rating estrellas. La fecha pasada se crea si no existe.
CREATE OR REPLACE FUNCTION pg_temp.demo_review(p_tourist_email TEXT, p_activity TEXT, p_days_ago INTEGER,
                                               p_people INTEGER, p_rating INTEGER, p_comment TEXT)
    RETURNS VOID AS $$
DECLARE
    v_tourist BIGINT;
    v_activity BIGINT;
    v_price NUMERIC;
    v_start TIMESTAMP;
    v_instance BIGINT;
    v_reservation BIGINT;
BEGIN
    SELECT id INTO v_tourist FROM app_user WHERE lower(email) = lower(p_tourist_email);

    -- Solo las actividades de ejemplo (las de los prestadores @demo.uy)
    SELECT a.id, a.price INTO v_activity, v_price
    FROM activity a
    JOIN business b ON b.id = a.business_id
    JOIN app_user u ON u.id = b.provider_id
    WHERE a.name = p_activity AND u.email LIKE '%@demo.uy'
    LIMIT 1;
    IF v_activity IS NULL THEN
        RAISE EXCEPTION 'No está la actividad de ejemplo "%". Corré antes 2026-10-28_demo_activities.sql', p_activity;
    END IF;

    -- ¿Ya la valoró? Entonces no se repite
    IF EXISTS (SELECT 1 FROM review rv
               JOIN reservation r ON r.id = rv.reservation_id
               JOIN activity_instance i ON i.id = r.instance_id
               WHERE r.tourist_id = v_tourist AND i.activity_id = v_activity) THEN
        RETURN;
    END IF;

    -- La fecha que ya pasó (a las 10:00), reutilizando la que ya exista ese día
    v_start := ((now() AT TIME ZONE 'America/Montevideo')::date - p_days_ago) + TIME '10:00';
    SELECT id INTO v_instance FROM activity_instance WHERE activity_id = v_activity AND start_at = v_start;
    IF v_instance IS NULL THEN
        INSERT INTO activity_instance (activity_id, start_at, max_capacity)
        VALUES (v_activity, v_start, 20)
        RETURNING id INTO v_instance;
    END IF;

    -- La reserva (se hizo unos días antes y ya terminó) y su valoración (al día siguiente)
    INSERT INTO reservation (tourist_id, instance_id, participants, total_price, status, created_at)
    VALUES (v_tourist, v_instance, p_people, v_price * p_people, 'FINISHED', v_start - INTERVAL '4 days')
    RETURNING id INTO v_reservation;

    INSERT INTO review (reservation_id, rating, comment, created_at)
    VALUES (v_reservation, p_rating, p_comment, v_start + INTERVAL '1 day 3 hours');
END $$ LANGUAGE plpgsql;

-- ---------- Turistas y valoraciones ----------

DO $$
BEGIN
    PERFORM pg_temp.demo_tourist('camila.gonzalez@demo.uy',  'Camila',    'González',  '48810011', '098455101');
    PERFORM pg_temp.demo_tourist('mateo.fernandez@demo.uy',  'Mateo',     'Fernández', '48810022', '098455102');
    PERFORM pg_temp.demo_tourist('valeria.lopez@demo.uy',    'Valeria',   'López',     '48810033', '098455103');
    PERFORM pg_temp.demo_tourist('nicolas.martinez@demo.uy', 'Nicolás',   'Martínez',  '48810044', '098455104');
    PERFORM pg_temp.demo_tourist('florencia.silva@demo.uy',  'Florencia', 'Silva',     '48810055', '098455105');
    PERFORM pg_temp.demo_tourist('santiago.pereira@demo.uy', 'Santiago',  'Pereira',   '48810066', '098455106');
    PERFORM pg_temp.demo_tourist('agustina.romero@demo.uy',  'Agustina',  'Romero',    '48810077', '098455107');
    PERFORM pg_temp.demo_tourist('joaquin.sosa@demo.uy',     'Joaquín',   'Sosa',      '48810088', '098455108');
    PERFORM pg_temp.demo_tourist('martina.acosta@demo.uy',   'Martina',   'Acosta',    '48810099', '098455109');
    PERFORM pg_temp.demo_tourist('federico.diaz@demo.uy',    'Federico',  'Díaz',      '48810101', '098455110');
    PERFORM pg_temp.demo_tourist('lucia.benitez@demo.uy',    'Lucía',     'Benítez',   '48810112', '098455111');
    PERFORM pg_temp.demo_tourist('emiliano.castro@demo.uy',  'Emiliano',  'Castro',    '48810123', '098455112');

    -- Rocha
    PERFORM pg_temp.demo_review('camila.gonzalez@demo.uy', 'Caminata a la lobería de Cabo Polonio', 12, 2, 5,
        'Increíble. Cruzar las dunas caminando y llegar al faro es otra cosa. Los lobos marinos a pocos metros. La guía sabía muchísimo.');
    PERFORM pg_temp.demo_review('mateo.fernandez@demo.uy', 'Caminata a la lobería de Cabo Polonio', 12, 1, 5,
        'De lo mejor que hice en Uruguay. Llevá buen calzado y agua, la arena cansa pero vale la pena.');
    PERFORM pg_temp.demo_review('florencia.silva@demo.uy', 'Caminata a la lobería de Cabo Polonio', 26, 3, 4,
        'Muy lindo paseo y bien organizado. Hubo mucho viento, así que no pudimos subir al faro, pero igual valió la pena.');
    PERFORM pg_temp.demo_review('joaquin.sosa@demo.uy', 'Caminata a la lobería de Cabo Polonio', 33, 2, 5,
        'Fuimos en pareja y nos encantó. El atardecer volviendo por la playa fue lo mejor.');
    PERFORM pg_temp.demo_review('valeria.lopez@demo.uy', 'Avistamiento de ballenas francas en La Paloma', 9, 2, 5,
        'Vimos una ballena con su cría muy cerca de la costa. Emocionante. Los binoculares que prestan son muy buenos.');
    PERFORM pg_temp.demo_review('nicolas.martinez@demo.uy', 'Avistamiento de ballenas francas en La Paloma', 16, 4, 4,
        'Al principio no aparecía ninguna y casi nos vamos, pero después vimos tres. El guía explica muy bien su comportamiento.');
    PERFORM pg_temp.demo_review('lucia.benitez@demo.uy', 'Avistamiento de ballenas francas en La Paloma', 23, 2, 3,
        'Lindo, pero ese día estaban lejos y se veían poco. No es culpa del guía, es la naturaleza.');

    -- Treinta y Tres
    PERFORM pg_temp.demo_review('santiago.pereira@demo.uy', 'Senderismo en la Quebrada de los Cuervos', 10, 2, 5,
        'Un paisaje que no parece Uruguay. La bajada tiene partes exigentes, pero el guía va al ritmo del grupo.');
    PERFORM pg_temp.demo_review('agustina.romero@demo.uy', 'Senderismo en la Quebrada de los Cuervos', 17, 1, 5,
        'Hermoso el monte nativo y el arroyo. Vimos jotes de cabeza amarilla. Recomiendo ir temprano.');
    PERFORM pg_temp.demo_review('federico.diaz@demo.uy', 'Senderismo en la Quebrada de los Cuervos', 31, 2, 4,
        'Muy buena caminata. La subida de vuelta es dura, hay que estar en forma.');

    -- Colonia
    PERFORM pg_temp.demo_review('martina.acosta@demo.uy', 'Recorrido por el Barrio Histórico de Colonia', 5, 2, 5,
        'La guía contó un montón de historias que no sabíamos. Las calles de piedra al atardecer son preciosas.');
    PERFORM pg_temp.demo_review('emiliano.castro@demo.uy', 'Recorrido por el Barrio Histórico de Colonia', 12, 4, 5,
        'Ideal para ir con la familia. Corto, entretenido y con muchas paradas para sacar fotos.');
    PERFORM pg_temp.demo_review('camila.gonzalez@demo.uy', 'Recorrido por el Barrio Histórico de Colonia', 19, 1, 4,
        'Muy lindo. Me hubiese gustado que durara un poco más, pero se entiende muy bien la historia de la ciudad.');
    PERFORM pg_temp.demo_review('valeria.lopez@demo.uy', 'Recorrido por el Barrio Histórico de Colonia', 26, 2, 5,
        'Excelente. La Calle de los Suspiros y el faro son imperdibles.');
    PERFORM pg_temp.demo_review('nicolas.martinez@demo.uy', 'Ruinas de la Calera de las Huérfanas', 14, 2, 4,
        'Un lugar con mucha historia y poca gente. La merienda de campo al final estuvo muy rica.');
    PERFORM pg_temp.demo_review('lucia.benitez@demo.uy', 'Ruinas de la Calera de las Huérfanas', 28, 3, 5,
        'No conocíamos este lugar y nos sorprendió. Muy bien explicado el trabajo de los jesuitas.');

    -- Montevideo
    PERFORM pg_temp.demo_review('mateo.fernandez@demo.uy', 'Asado y historia en el Mercado del Puerto', 4, 2, 5,
        'El asado espectacular y el recorrido por la Ciudad Vieja muy ameno. El medio y medio, un clásico.');
    PERFORM pg_temp.demo_review('florencia.silva@demo.uy', 'Asado y historia en el Mercado del Puerto', 6, 4, 5,
        'Llevamos a unos amigos de afuera y quedaron encantados. Mucha comida, vengan con hambre.');
    PERFORM pg_temp.demo_review('santiago.pereira@demo.uy', 'Asado y historia en el Mercado del Puerto', 13, 1, 4,
        'Muy bueno. Un sábado al mediodía hay mucha gente, pero el guía tenía la mesa reservada.');
    PERFORM pg_temp.demo_review('joaquin.sosa@demo.uy', 'Asado y historia en el Mercado del Puerto', 20, 2, 5,
        'Riquísimo todo. La historia del edificio de hierro que vino de Inglaterra no la sabía.');
    PERFORM pg_temp.demo_review('agustina.romero@demo.uy', 'Bicicleteada por la Rambla de Montevideo', 7, 2, 5,
        'Re lindo pedalear por la rambla con el río al lado. Las bicis estaban impecables.');
    PERFORM pg_temp.demo_review('martina.acosta@demo.uy', 'Bicicleteada por la Rambla de Montevideo', 15, 1, 4,
        'Tranqui y apto para todos. Ideal para conocer Pocitos y Punta Carretas sin cansarse.');
    PERFORM pg_temp.demo_review('federico.diaz@demo.uy', 'Visita y cata de tannat en bodega', 8, 2, 5,
        'La cata de tannat, excelente, y los quesos también. Los dueños te cuentan todo el proceso con mucha pasión.');
    PERFORM pg_temp.demo_review('emiliano.castro@demo.uy', 'Visita y cata de tannat en bodega', 22, 2, 4,
        'Muy linda bodega y buenos vinos. Recomiendo ir en día de semana, hay menos gente.');

    -- Maldonado y Lavalleja
    PERFORM pg_temp.demo_review('camila.gonzalez@demo.uy', 'Ascenso al Cerro Pan de Azúcar', 11, 1, 5,
        'La vista desde arriba es impresionante, se ve hasta Piriápolis. Hay que ir con calzado de trekking.');
    PERFORM pg_temp.demo_review('nicolas.martinez@demo.uy', 'Ascenso al Cerro Pan de Azúcar', 18, 2, 4,
        'Exigente pero muy lindo. La reserva de fauna al bajar es un buen cierre.');
    PERFORM pg_temp.demo_review('lucia.benitez@demo.uy', 'Ascenso al Cerro Pan de Azúcar', 34, 1, 5,
        'Hermoso. El guía nos esperó en cada tramo difícil.');
    PERFORM pg_temp.demo_review('valeria.lopez@demo.uy', 'Kayak en la Laguna Garzón', 3, 2, 5,
        'Nunca había hecho kayak y fue facilísimo. El agua muy tranquila y vimos cisnes de cuello negro.');
    PERFORM pg_temp.demo_review('santiago.pereira@demo.uy', 'Kayak en la Laguna Garzón', 10, 2, 5,
        'Hermosa experiencia. Pasar por abajo del puente redondo es lo más.');
    PERFORM pg_temp.demo_review('martina.acosta@demo.uy', 'Kayak en la Laguna Garzón', 24, 3, 4,
        'Muy lindo paseo. Llevá protector solar y gorro, no hay sombra en la laguna.');
    PERFORM pg_temp.demo_review('mateo.fernandez@demo.uy', 'Cabalgata por las sierras de Lavalleja', 6, 2, 5,
        'Los caballos son muy mansos y el paisaje de las sierras es bárbaro. El mate en el mirador, un lujo.');
    PERFORM pg_temp.demo_review('agustina.romero@demo.uy', 'Cabalgata por las sierras de Lavalleja', 20, 1, 4,
        'Muy linda cabalgata. Al otro día me dolían las piernas, pero valió la pena.');
    PERFORM pg_temp.demo_review('joaquin.sosa@demo.uy', 'Cabalgata por las sierras de Lavalleja', 27, 2, 5,
        'La mejor actividad del viaje. Muy atentos con los que nunca habíamos montado.');

    -- Salto y Río Negro
    PERFORM pg_temp.demo_review('florencia.silva@demo.uy', 'Día de termas en Daymán', 5, 4, 5,
        'Ideal para descansar. Los chicos no querían salir de la piscina. El traslado, puntual.');
    PERFORM pg_temp.demo_review('federico.diaz@demo.uy', 'Día de termas en Daymán', 14, 2, 4,
        'Muy relajante. Las piscinas cerradas son las mejores si hace frío.');
    PERFORM pg_temp.demo_review('emiliano.castro@demo.uy', 'Día de termas en Daymán', 21, 2, 4,
        'Buen día de termas. El almuerzo podría mejorar, pero todo lo demás muy bien.');
    PERFORM pg_temp.demo_review('camila.gonzalez@demo.uy', 'Navegación por los Esteros de Farrapos', 9, 2, 5,
        'Un paraíso escondido. Vimos carpinchos y un montón de aves. El guía conoce cada rincón de los esteros.');
    PERFORM pg_temp.demo_review('lucia.benitez@demo.uy', 'Navegación por los Esteros de Farrapos', 30, 2, 5,
        'Paz total. La lancha cómoda y el paseo muy bien explicado. Volvería.');

    -- Tacuarembó y Florida
    PERFORM pg_temp.demo_review('valeria.lopez@demo.uy', 'Sendero a Pozo Hondo en Valle Edén', 13, 2, 5,
        'La cascada de Pozo Hondo es hermosa y el monte muy fresco. El museo de Gardel, un plus.');
    PERFORM pg_temp.demo_review('santiago.pereira@demo.uy', 'Sendero a Pozo Hondo en Valle Edén', 27, 1, 4,
        'Muy lindo lugar y bien cuidado. Algunos tramos tienen barro si llovió.');
    PERFORM pg_temp.demo_review('nicolas.martinez@demo.uy', 'Día de campo en estancia', 4, 5, 5,
        'Un día de campo de verdad: el asado con cuero, el trabajo con el ganado y los peones con mil historias.');
    PERFORM pg_temp.demo_review('martina.acosta@demo.uy', 'Día de campo en estancia', 11, 3, 5,
        'Fuimos con mis padres y mis hijos y todos la pasamos bárbaro. La comida, abundante y casera.');
    PERFORM pg_temp.demo_review('joaquin.sosa@demo.uy', 'Día de campo en estancia', 18, 2, 4,
        'Muy auténtico. El paseo en carro estuvo divertido. Es un día largo, ir con tiempo.');
    PERFORM pg_temp.demo_review('agustina.romero@demo.uy', 'Día de campo en estancia', 32, 4, 5,
        'Excelente atención de la familia que la atiende. Nos sentimos como en casa.');
END $$;

-- Control: cuántas valoraciones y qué promedio quedó en cada actividad de ejemplo
SELECT a.name AS actividad, count(rv.id) AS valoraciones, round(avg(rv.rating), 1) AS promedio
FROM activity a
JOIN business b ON b.id = a.business_id
JOIN app_user u ON u.id = b.provider_id
LEFT JOIN activity_instance i ON i.activity_id = a.id
LEFT JOIN reservation r ON r.instance_id = i.id
LEFT JOIN review rv ON rv.reservation_id = r.id
WHERE u.email LIKE '%@demo.uy'
GROUP BY a.name
ORDER BY promedio DESC NULLS LAST, valoraciones DESC;

-- Para borrar más adelante SOLO estas valoraciones de ejemplo (descomentar y correr aparte, ANTES de
-- borrar las actividades de ejemplo):
-- DELETE FROM review WHERE reservation_id IN (SELECT r.id FROM reservation r JOIN app_user u ON u.id = r.tourist_id WHERE u.email LIKE '%@demo.uy');
-- DELETE FROM reservation WHERE tourist_id IN (SELECT id FROM app_user WHERE email LIKE '%@demo.uy');
-- DELETE FROM tourist WHERE user_id IN (SELECT id FROM app_user WHERE email LIKE '%@demo.uy');
-- DELETE FROM app_user u WHERE u.email LIKE '%@demo.uy' AND NOT EXISTS (SELECT 1 FROM provider p WHERE p.user_id = u.id);
