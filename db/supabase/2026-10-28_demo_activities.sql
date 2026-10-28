-- Datos de ejemplo para que el portal se vea completo: 7 prestadores aprobados, cada uno con su
-- emprendimiento, y 15 actividades típicas de Uruguay en 10 departamentos, con foto y fechas
-- en las próximas semanas.
--
-- * Las fotos son de Wikimedia Commons (licencias libres). Se guarda solo su dirección, no la
--   imagen: el script no pesa en la base (unas 15 filas de texto por tabla).
-- * Las fechas se calculan desde HOY, así siempre hay salidas próximas.
-- * Es seguro correrlo más de una vez: un prestador cuyo correo ya existe, o una actividad que ya
--   existe en ese emprendimiento, se saltea. No modifica ni borra datos existentes.
-- * Requiere haber corrido antes 2026-10-24_localities.sql y 2026-10-28_activity_photo.sql
--   (igual, las columnas de la foto se agregan acá por las dudas).
--
-- Los prestadores de ejemplo pueden iniciar sesión con su correo y la contraseña Demo2026
-- (por ejemplo costarochense@demo.uy). Para borrarlos: ver el final del archivo.

-- La contraseña se cifra con BCrypt (pgcrypto ya viene instalado en Supabase)
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA extensions;

-- Por si no se corrió la versión nueva del script de fotos
ALTER TABLE activity_photo ADD COLUMN IF NOT EXISTS external_url VARCHAR(500);
ALTER TABLE activity_photo ALTER COLUMN image DROP NOT NULL;
ALTER TABLE activity_photo ALTER COLUMN content_type DROP NOT NULL;
ALTER TABLE activity_photo DROP CONSTRAINT IF EXISTS activity_photo_content_type_check;

-- Tipos de actividad que usan las actividades de ejemplo (si ya existen, no se tocan)
INSERT INTO activity_type (name, active)
SELECT v.name, true
FROM (VALUES ('Actividades acuáticas'), ('Aventura'), ('Avistamiento de ballenas'), ('Cabalgata'),
             ('Enoturismo'), ('Gastronomía'), ('Recorridos históricos'), ('Senderismo'), ('Trekking'),
             ('Turismo de naturaleza')) AS v(name)
WHERE NOT EXISTS (SELECT 1 FROM activity_type t
                  WHERE lower(translate(t.name, 'ÁÉÍÓÚáéíóú', 'AEIOUaeiou'))
                      = lower(translate(v.name, 'ÁÉÍÓÚáéíóú', 'AEIOUaeiou')));

-- ---------- Funciones de ayuda (temporales: desaparecen al terminar) ----------

-- La localidad, buscando departamento y localidad sin importar tildes ni mayúsculas
CREATE OR REPLACE FUNCTION pg_temp.demo_locality(p_department TEXT, p_locality TEXT) RETURNS INTEGER AS $$
DECLARE
    v_id INTEGER;
BEGIN
    SELECT l.id INTO v_id
    FROM locality l JOIN department d ON d.id = l.department_id
    WHERE lower(translate(d.name, 'ÁÉÍÓÚáéíóú', 'AEIOUaeiou')) = lower(translate(p_department, 'ÁÉÍÓÚáéíóú', 'AEIOUaeiou'))
      AND lower(translate(l.name, 'ÁÉÍÓÚáéíóú', 'AEIOUaeiou')) = lower(translate(p_locality, 'ÁÉÍÓÚáéíóú', 'AEIOUaeiou'))
    LIMIT 1;
    IF v_id IS NULL THEN
        RAISE EXCEPTION 'No existe la localidad % (%). Corré antes 2026-10-24_localities.sql', p_locality, p_department;
    END IF;
    RETURN v_id;
END $$ LANGUAGE plpgsql;

-- Un prestador (persona física, aprobado) con su emprendimiento. Devuelve el id del emprendimiento.
CREATE OR REPLACE FUNCTION pg_temp.demo_provider(p_email TEXT, p_first TEXT, p_last TEXT, p_document TEXT,
                                                 p_phone TEXT, p_department TEXT, p_locality TEXT,
                                                 p_business TEXT, p_description TEXT, p_address TEXT)
    RETURNS BIGINT AS $$
DECLARE
    v_user BIGINT;
    v_business BIGINT;
BEGIN
    SELECT id INTO v_user FROM app_user WHERE lower(email) = lower(p_email);
    IF v_user IS NOT NULL THEN
        SELECT id INTO v_business FROM business WHERE provider_id = v_user;
        RETURN v_business; -- ya estaba cargado
    END IF;

    INSERT INTO app_user (email, password_hash, phone)
    VALUES (p_email, extensions.crypt('Demo2026', extensions.gen_salt('bf', 10)), p_phone)
    RETURNING id INTO v_user;

    INSERT INTO provider (user_id, person_type, status, first_name, last_name, document, address)
    VALUES (v_user, 'PHYSICAL', 'APPROVED', p_first, p_last, p_document, p_address);

    INSERT INTO business (provider_id, locality_id, name, description, address, phone, email)
    VALUES (v_user, pg_temp.demo_locality(p_department, p_locality), p_business, p_description,
            p_address, p_phone, p_email)
    RETURNING id INTO v_business;
    RETURN v_business;
END $$ LANGUAGE plpgsql;

-- Una actividad publicada, con sus tipos, su foto y sus fechas (días desde hoy, a una hora fija)
CREATE OR REPLACE FUNCTION pg_temp.demo_activity(p_business BIGINT, p_department TEXT, p_locality TEXT,
                                                 p_name TEXT, p_description TEXT, p_minutes INTEGER,
                                                 p_price NUMERIC, p_min_age INTEGER, p_types TEXT[],
                                                 p_photo TEXT, p_capacity INTEGER, p_days INTEGER[],
                                                 p_time TIME)
    RETURNS VOID AS $$
DECLARE
    v_activity BIGINT;
    v_today DATE := (now() AT TIME ZONE 'America/Montevideo')::date;
    v_day INTEGER;
BEGIN
    IF p_business IS NULL OR EXISTS (SELECT 1 FROM activity WHERE business_id = p_business AND name = p_name) THEN
        RETURN; -- ya estaba cargada
    END IF;

    INSERT INTO activity (business_id, locality_id, name, description, duration_minutes, price, min_age,
                          booking_cutoff_minutes, active)
    VALUES (p_business, pg_temp.demo_locality(p_department, p_locality), p_name, p_description, p_minutes,
            p_price, p_min_age, 120, true)
    RETURNING id INTO v_activity;

    INSERT INTO activity_activity_type (activity_id, activity_type_id)
    SELECT v_activity, t.id
    FROM activity_type t
    WHERE lower(translate(t.name, 'ÁÉÍÓÚáéíóú', 'AEIOUaeiou')) IN
          (SELECT lower(translate(x, 'ÁÉÍÓÚáéíóú', 'AEIOUaeiou')) FROM unnest(p_types) AS x);

    INSERT INTO activity_photo (activity_id, external_url, updated_at)
    VALUES (v_activity, p_photo, now() AT TIME ZONE 'America/Montevideo');

    FOREACH v_day IN ARRAY p_days LOOP
        INSERT INTO activity_instance (activity_id, start_at, max_capacity)
        VALUES (v_activity, (v_today + v_day) + p_time, p_capacity);
    END LOOP;
END $$ LANGUAGE plpgsql;

-- ---------- Prestadores y actividades ----------

DO $$
DECLARE
    wiki CONSTANT TEXT := 'https://commons.wikimedia.org/wiki/Special:FilePath/';
    ancho CONSTANT TEXT := '?width=1600';
    b BIGINT;
BEGIN
    -- ROCHA
    b := pg_temp.demo_provider('costarochense@demo.uy', 'Lucía', 'Ferreira', '49900001', '099123401',
        'Rocha', 'La Paloma', 'Costa Rochense Turismo',
        'Guías locales de La Paloma. Salidas a la costa, las lagunas y Cabo Polonio.', 'Av. Solari s/n, La Paloma');
    PERFORM pg_temp.demo_activity(b, 'Rocha', 'Cabo Polonio', 'Caminata a la lobería de Cabo Polonio',
        'Cruzamos las dunas del Parque Nacional hasta el faro y la lobería, donde viven lobos y leones marinos. '
        || 'Incluye la entrada al faro y una charla sobre la historia del pueblo, que no tiene luz eléctrica de red.',
        180, 1800, 0, ARRAY['Senderismo', 'Turismo de naturaleza'],
        wiki || 'Cabo_Polonio,_vista_desde_las_dunas.jpg' || ancho, 15, ARRAY[2, 6, 9, 13, 20, 27], '10:00');
    PERFORM pg_temp.demo_activity(b, 'Rocha', 'La Paloma', 'Avistamiento de ballenas francas en La Paloma',
        'Entre julio y noviembre la ballena franca austral se acerca a la costa de Rocha para tener sus crías. '
        || 'Las miramos desde la costa con binoculares y un guía que explica su comportamiento.',
        120, 1200, 0, ARRAY['Avistamiento de ballenas', 'Turismo de naturaleza'],
        wiki || 'Southern_right_whale.jpg' || ancho, 20, ARRAY[1, 4, 8, 11, 15], '09:30');

    -- TREINTA Y TRES
    b := pg_temp.demo_provider('senderosdeleste@demo.uy', 'Martín', 'Olivera', '49900002', '099123402',
        'Treinta y Tres', 'Treinta y Tres', 'Senderos del Este',
        'Caminatas guiadas por las sierras y quebradas del este del país.', 'Juan A. Lavalleja 1250, Treinta y Tres');
    PERFORM pg_temp.demo_activity(b, 'Treinta y Tres', 'Treinta y Tres', 'Senderismo en la Quebrada de los Cuervos',
        'Bajamos al fondo de la quebrada, el primer Área Protegida del país, entre monte nativo, arroyos y paredes '
        || 'de piedra. Se ven jotes y cuervos de cabeza amarilla. Nivel medio: hay tramos con pendiente.',
        300, 1500, 10, ARRAY['Senderismo', 'Trekking', 'Turismo de naturaleza'],
        wiki || 'Quebrada_de_los_Cuervos,Treinta_y_Tres.JPG' || ancho, 12, ARRAY[3, 10, 17, 24], '08:30');

    -- COLONIA
    b := pg_temp.demo_provider('coloniapatrimonio@demo.uy', 'Sofía', 'Méndez', '49900003', '099123403',
        'Colonia', 'Colonia del Sacramento', 'Colonia Patrimonio',
        'Recorridos por el Barrio Histórico de Colonia y sus alrededores, con guías habilitados.', 'Calle de San Pedro 120, Colonia del Sacramento');
    PERFORM pg_temp.demo_activity(b, 'Colonia', 'Colonia del Sacramento', 'Recorrido por el Barrio Histórico de Colonia',
        'Caminamos por las calles de piedra del Barrio Histórico, Patrimonio de la Humanidad: la Calle de los '
        || 'Suspiros, el Portón de Campo, la Basílica y el faro. Historia de la ciudad fundada por los portugueses en 1680.',
        120, 900, 0, ARRAY['Recorridos históricos'],
        wiki || 'Calle_en_el_Barrio_Hist%C3%B3rico_de_Colonia_del_Sacramento.jpg' || ancho, 20, ARRAY[1, 3, 5, 8, 12, 19], '16:00');
    PERFORM pg_temp.demo_activity(b, 'Colonia', 'Carmelo', 'Ruinas de la Calera de las Huérfanas',
        'Visita a la calera jesuita del siglo XVIII cerca de Carmelo: hornos de cal, la capilla y la historia de las '
        || 'estancias jesuíticas en la Banda Oriental. Termina con una merienda de campo.',
        150, 1100, 0, ARRAY['Recorridos históricos', 'Turismo de naturaleza'],
        wiki || 'Calera_de_las_Hu%C3%A9rfanas,_pr%C3%B3xima_a_ciudad_de_Carmelo,_Colonia.JPG' || ancho, 15, ARRAY[4, 11, 18], '15:00');

    -- MONTEVIDEO
    b := pg_temp.demo_provider('montevideoapie@demo.uy', 'Diego', 'Rodríguez', '49900004', '099123404',
        'Montevideo', 'Montevideo', 'Montevideo a Pie y en Bici',
        'Paseos por Montevideo: su rambla, su comida y sus bodegas.', 'Rambla 25 de Agosto 228, Montevideo');
    PERFORM pg_temp.demo_activity(b, 'Montevideo', 'Montevideo', 'Asado y historia en el Mercado del Puerto',
        'Recorremos la Ciudad Vieja y el Mercado del Puerto, una estructura de hierro de 1868, y almorzamos en una '
        || 'de sus parrillas: asado, chorizo, morcilla y el clásico medio y medio.',
        150, 2400, 0, ARRAY['Gastronomía', 'Recorridos históricos'],
        wiki || 'Mercado_del_Puerto.jpg' || ancho, 16, ARRAY[2, 5, 9, 12, 16, 23], '12:00');
    PERFORM pg_temp.demo_activity(b, 'Montevideo', 'Montevideo', 'Bicicleteada por la Rambla de Montevideo',
        'Pedaleamos por la rambla desde Pocitos hasta Punta Carretas y el Parque Rodó, con paradas en los miradores. '
        || 'Incluye bicicleta y casco. Recorrido llano, apto para principiantes.',
        120, 950, 8, ARRAY['Aventura'],
        wiki || 'Rambla_de_Montevideo,_Uruguay.jpg' || ancho, 12, ARRAY[1, 6, 13, 20], '17:30');
    PERFORM pg_temp.demo_activity(b, 'Montevideo', 'Montevideo', 'Visita y cata de tannat en bodega',
        'Visita a una bodega familiar en las afueras de Montevideo: viñedos, sala de barricas y cata de tres vinos, '
        || 'entre ellos el tannat, la uva insignia de Uruguay. Con quesos y panes caseros.',
        180, 2900, 18, ARRAY['Enoturismo', 'Gastronomía'],
        wiki || 'Bodega_Bouza_-_Montevid%C3%A9u,_Uruguai_-_panoramio.jpg' || ancho, 14, ARRAY[3, 10, 17, 24], '11:00');

    -- MALDONADO y LAVALLEJA
    b := pg_temp.demo_provider('sierrasylagunas@demo.uy', 'Valentina', 'Suárez', '49900005', '099123405',
        'Maldonado', 'Pan de Azúcar', 'Sierras y Lagunas',
        'Aventura y naturaleza entre las sierras de Maldonado y Lavalleja y las lagunas de la costa.', 'Ruta 9 km 90, Pan de Azúcar');
    PERFORM pg_temp.demo_activity(b, 'Maldonado', 'Pan de Azúcar', 'Ascenso al Cerro Pan de Azúcar',
        'Subimos por el sendero hasta la cruz del tercer cerro más alto del país, con vista a Piriápolis y al mar. '
        || 'Al bajar visitamos la Reserva de Fauna Autóctona. Nivel medio-alto.',
        210, 1300, 12, ARRAY['Trekking', 'Aventura'],
        wiki || 'Cerro_Pan_de_Azucar_-_Ruta_9_Maldonado_Uruguay_-_panoramio_(1).jpg' || ancho, 12, ARRAY[2, 7, 14, 21], '09:00');
    PERFORM pg_temp.demo_activity(b, 'Maldonado', 'Garzón', 'Kayak en la Laguna Garzón',
        'Remamos por la laguna, entre aves acuáticas y el puente circular de Rafael Viñoly. Aguas tranquilas, '
        || 'ideal para quienes nunca hicieron kayak. Incluye kayak, chaleco y guía.',
        150, 1700, 10, ARRAY['Actividades acuáticas', 'Turismo de naturaleza'],
        wiki || 'Laguna_Garzon_01.jpg' || ancho, 10, ARRAY[1, 5, 8, 12, 19], '16:30');
    PERFORM pg_temp.demo_activity(b, 'Lavalleja', 'Villa Serrana', 'Cabalgata por las sierras de Lavalleja',
        'Salimos a caballo desde Villa Serrana por las sierras y quebradas, hasta un mirador sobre el valle. '
        || 'Caballos criollos mansos, aptos para quienes nunca montaron.',
        180, 2200, 8, ARRAY['Cabalgata', 'Turismo de naturaleza'],
        wiki || 'Cerros_de_Lavalleja.jpg' || ancho, 8, ARRAY[3, 6, 10, 17], '10:00');

    -- SALTO y RÍO NEGRO
    b := pg_temp.demo_provider('litoraltermal@demo.uy', 'Andrés', 'Pereira', '49900006', '099123406',
        'Salto', 'Salto', 'Litoral Termal',
        'Paseos por el litoral del río Uruguay: termas, esteros e islas.', 'Uruguay 850, Salto');
    PERFORM pg_temp.demo_activity(b, 'Salto', 'Termas del Daymán', 'Día de termas en Daymán',
        'Un día en las Termas del Daymán: piscinas de agua termal a 38 °C, almuerzo y tiempo libre. '
        || 'Incluye traslado desde Salto y entrada al parque termal.',
        360, 1600, 0, ARRAY['Turismo de naturaleza'],
        wiki || 'Termas_de_Daym%C3%A1n.jpg' || ancho, 25, ARRAY[2, 4, 9, 16, 23], '09:00');
    PERFORM pg_temp.demo_activity(b, 'Río Negro', 'Nuevo Berlín', 'Navegación por los Esteros de Farrapos',
        'Navegamos entre las islas y los esteros del río Uruguay, un humedal Ramsar con carpinchos, lobitos de río y '
        || 'más de 200 especies de aves. Salida en lancha con guía naturalista.',
        180, 2000, 0, ARRAY['Actividades acuáticas', 'Turismo de naturaleza'],
        wiki || 'Esteros_de_Farrapos_5068373225_o.jpg' || ancho, 12, ARRAY[5, 12, 19, 26], '14:00');

    -- TACUAREMBÓ y FLORIDA
    b := pg_temp.demo_provider('estanciasdelnorte@demo.uy', 'Juan', 'Da Silva', '49900007', '099123407',
        'Tacuarembó', 'Tacuarembó', 'Estancias del Norte',
        'Turismo rural en estancias: la vida de campo, el caballo y la comida criolla.', '18 de Julio 300, Tacuarembó');
    PERFORM pg_temp.demo_activity(b, 'Tacuarembó', 'Valle Edén', 'Sendero a Pozo Hondo en Valle Edén',
        'Caminata por el monte nativo de Valle Edén hasta Pozo Hondo, una cascada con una gran olla de agua '
        || 'donde se puede nadar en verano. Visitamos también el pueblo y el museo de Carlos Gardel.',
        240, 1400, 6, ARRAY['Senderismo', 'Turismo de naturaleza'],
        wiki || 'Pozo_Hondo_(Tacuaremb%C3%B3).jpg' || ancho, 15, ARRAY[4, 11, 18, 25], '10:30');
    PERFORM pg_temp.demo_activity(b, 'Florida', 'Florida', 'Día de campo en estancia',
        'Un día en una estancia ganadera: recorrida a caballo o en carro, mate con los peones, trabajo con el ganado '
        || 'y asado con cuero al mediodía. Para toda la familia.',
        420, 3200, 0, ARRAY['Cabalgata', 'Gastronomía'],
        wiki || 'Estanciauruguay.jpg' || ancho, 20, ARRAY[3, 7, 14, 21, 28], '09:30');
END $$;

-- Control: las actividades de ejemplo, con su departamento y cuántas fechas tienen
SELECT a.name AS actividad, d.name AS departamento, bz.name AS emprendimiento, count(i.id) AS fechas
FROM activity a
JOIN business bz ON bz.id = a.business_id
JOIN app_user u ON u.id = bz.provider_id
JOIN locality l ON l.id = a.locality_id
JOIN department d ON d.id = l.department_id
LEFT JOIN activity_instance i ON i.activity_id = a.id
WHERE u.email LIKE '%@demo.uy'
GROUP BY a.name, d.name, bz.name
ORDER BY d.name, a.name;

-- Para borrar TODO lo de ejemplo más adelante (solo afecta a los correos @demo.uy y lo que cuelga de
-- ellos; descomentar y correr aparte). Primero las fechas sin reservas, luego lo demás:
-- DELETE FROM activity_instance WHERE activity_id IN (SELECT a.id FROM activity a JOIN business b ON b.id = a.business_id JOIN app_user u ON u.id = b.provider_id WHERE u.email LIKE '%@demo.uy');
-- DELETE FROM activity_photo WHERE activity_id IN (SELECT a.id FROM activity a JOIN business b ON b.id = a.business_id JOIN app_user u ON u.id = b.provider_id WHERE u.email LIKE '%@demo.uy');
-- DELETE FROM activity_activity_type WHERE activity_id IN (SELECT a.id FROM activity a JOIN business b ON b.id = a.business_id JOIN app_user u ON u.id = b.provider_id WHERE u.email LIKE '%@demo.uy');
-- DELETE FROM activity WHERE business_id IN (SELECT b.id FROM business b JOIN app_user u ON u.id = b.provider_id WHERE u.email LIKE '%@demo.uy');
-- DELETE FROM business WHERE provider_id IN (SELECT id FROM app_user WHERE email LIKE '%@demo.uy');
-- DELETE FROM provider WHERE user_id IN (SELECT id FROM app_user WHERE email LIKE '%@demo.uy');
-- DELETE FROM app_user WHERE email LIKE '%@demo.uy';
