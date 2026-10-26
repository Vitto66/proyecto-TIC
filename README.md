# Uruguay Mnatural — OASIS

Plataforma turística para el proyecto de Proyectos TIC 2026 (UM). Backend en Spring Boot + Maven.

## Correr el proyecto

Requisitos: Java 17 o superior. Maven no hace falta instalarlo, se usa `./mvnw`.

### Opción 1: base H2 en memoria (por defecto)

No requiere configuración. Se borra al apagar la app.

```
./mvnw spring-boot:run
```

Consola de la base: http://localhost:8080/h2-console (JDBC URL `jdbc:h2:mem:proytic`, usuario `sa`, sin contraseña).

### Opción 2: base compartida en Supabase

1. Pedí la contraseña de la base al equipo **por privado**.
2. En IntelliJ: **Run → Edit Configurations → Proytic2026Application**.
   - **Active profiles:** `postgres`
   - **Environment variables:** `DB_PASSWORD=<la contraseña>`
3. Correr la app.

Desde la terminal: `DB_PASSWORD='<la contraseña>' ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres`

## Administrador

No hay registro público de administradores. Al arrancar, si no existe ninguno, la app crea el
primero con las variables `ADMIN_EMAIL`, `ADMIN_PASSWORD` y `ADMIN_NAME` (opcional).

- **H2 local:** se crea solo, con `admin@uruguaymnatural.uy` / `admin1234`. Sirve para probar el CU-03 en tu compu.
- **Supabase:** la contraseña no tiene valor por defecto. La primera vez, quien lo cree agrega
  `ADMIN_PASSWORD=<contraseña>` (y si quiere `ADMIN_EMAIL`) a las variables de entorno de IntelliJ y arranca
  la app una vez. Después se puede sacar: si ya hay un admin, no hace nada.

## Páginas y endpoints

| Qué | Dónde |
| --- | --- |
| Menú inicial (público) | http://localhost:8080/ |
| Explorar actividades con filtros (CU-05, público) | http://localhost:8080/activities |
| Detalle de una actividad con fechas, valoraciones y clima (CU-06, público) | `/activities/{id}` (desde la vidriera) |
| Reservar una actividad (CU-14, turista) | `/tourist/activities/{id}/reserve` (botón "Reservar" del detalle) |
| Mis reservas: calendario con filtros (CU-17, turista) | `/tourist/reservations` (`?month=2026-11&day=2026-11-01&type=3`, todos opcionales) |
| Una reserva del turista (CU-14) | `/tourist/reservations/{id}` |
| Valorar una reserva finalizada (CU-18, turista) | `/tourist/reservations/{id}/review` (botón "Valorar" de la reserva o de Mis reservas) |
| Pagar una reserva (CU-15, turista) | `/tourist/reservations/{id}/pay` (botón "Pagar" de la reserva) |
| Avisos del turista, por ejemplo una reserva vencida (CU-16) | Arriba en `/tourist`; "Marcar como vistos" hace `POST /tourist/notifications/read` |
| Iniciar sesión (CU-04) | http://localhost:8080/login |
| Elegir perfil, si el correo tiene más de uno (CU-04) | http://localhost:8080/login/profile |
| Espacio del turista / prestador / administrador | `/tourist`, `/provider`, `/admin` (solo con sesión y ese perfil) |
| Formulario de registro de turistas (CU-01) | http://localhost:8080/register/tourist |
| Formulario de registro de prestadores (CU-02) | http://localhost:8080/register/provider |
| Revisar prestadores pendientes (CU-03, administrador) | http://localhost:8080/admin/providers |
| Registrar / ver mi emprendimiento (CU-07, prestador aprobado) | http://localhost:8080/provider/business |
| Mis actividades y publicar una nueva (CU-08, con emprendimiento) | http://localhost:8080/provider/activities |
| Modificar o dar de baja una actividad (CU-10) | `/provider/activities/{id}/edit` y `/provider/activities/{id}/cancel` (desde "Mis actividades") |
| Fechas, horarios y cupos de una actividad (CU-09) | `/provider/activities/{id}/dates` (desde "Mis actividades") |
| API: registrar turista (CU-01) | `POST /api/auth/register/tourist` |
| API: registrar prestador (CU-02) | `POST /api/auth/register/provider` |

Las páginas usan Thymeleaf + Bootstrap: las plantillas están en `src/main/resources/templates`
y los estilos, scripts e imágenes en `src/main/resources/static`.

## Emprendimiento (CU-07)

- Solo un prestador **aprobado** puede registrarlo, y cada prestador tiene **uno solo** (la base lo
  asegura con `UNIQUE (provider_id)`). Si ya lo registró, la página lo muestra con un aviso.
- Si el prestador es persona jurídica, el nombre llega precargado con el que puso al registrarse.
- La ubicación se elige de las tablas `department` y `locality`, que ya tienen datos en Supabase. Si a un
  departamento le faltan localidades, el formulario lo avisa: hay que cargarlas en Supabase.
- **No necesita scripts SQL:** usa las tablas `business`, `department` y `locality` que ya existen.

## Actividades (CU-08)

- Para publicar, el prestador tiene que estar aprobado y tener su emprendimiento registrado.
- Se eligen uno o más **tipos de actividad** y, opcionalmente, el **equipamiento**, indicando si lo pone
  el prestador o lo trae el turista. Solo se ofrecen los tipos activos que definió el administrador.
- La duración se carga en minutos y el precio por persona en pesos uruguayos.
- La actividad queda publicada (`active = true`) y sin fechas: las fechas y cupos son el CU-09.
- **No necesita scripts SQL:** usa `activity`, `activity_activity_type`, `activity_equipment`,
  `activity_type` y `equipment_type`, que ya existen en Supabase.

## Modificar o dar de baja una actividad (CU-10)

- Desde "Mis actividades", cada actividad publicada tiene **Modificar** y **Dar de baja**.
- **Modificar** usa el mismo formulario que publicar, con los datos actuales. Si cambia el precio, rige solo para
  reservas nuevas (3a): cada reserva guarda su precio total al crearse. Un tipo o equipamiento que el administrador
  desactivó después se puede mantener, pero no agregar.
- **Dar de baja** primero muestra cuántas reservas en fechas futuras se ven afectadas y pide confirmación (2a). Al
  confirmar (`ActivityCancellationService`):
  - la actividad queda con `active = false` y sale del portal;
  - las reservas **confirmadas** pasan a **CANCELLED** y se le pide a Mercado Pago (o al pago simulado) el
    **reembolso del 100%** (2a);
  - las **pendientes** pasan a **CANCELLED** (2b). Si un intento de pago había quedado en revisión o sin respuesta,
    se consulta y, si se llegó a cobrar, también se devuelve;
  - se avisa a cada turista (`TouristNotifier`: hoy en el log y en la pantalla de su reserva, que muestra
    "Cancelada" y si se le devolvió el dinero).
- Si el servicio de pagos no responde, la reserva igual queda cancelada y la devolución queda **pendiente**: en
  "Mis actividades" aparece **Reintentar devolución**.
- Las reservas de fechas que ya pasaron no se tocan. Una actividad dada de baja no se puede modificar.
- **Necesita el script** `2026-10-26_cu10_cancel_activity.sql`, que agrega el estado `CANCELLED` a las reservas.

## Fechas y cupos (CU-09)

- Cada fecha (`activity_instance`) dura lo que dura la actividad. El cupo máximo es opcional: vacío = sin límite.
- Solo se aceptan fechas futuras, en **hora de Uruguay** (`ClockConfig`), aunque el servidor esté en otra zona.
- Si una fecha nueva se pisa con otra de la misma actividad, se avisa y se pide confirmación. Dos fechas que
  empiezan exactamente a la misma hora no se permiten (la base tiene `UNIQUE (activity_id, start_at)`).
- Los lugares ocupados son los de las reservas **confirmadas o finalizadas**: una reserva pendiente todavía no
  ocupa lugar (CU-14) y una vencida ya no (CU-16).
- **No necesita scripts SQL:** usa `activity_instance` y `reservation`, que ya existen en Supabase.

## Explorar actividades (CU-05)

- Es público: no hace falta cuenta. Muestra solo actividades publicadas (`active = true`).
- Sin filtros, ordena de mejor a peor calificadas (promedio de `review`); las que no tienen valoraciones van al final.
- Filtros: departamento, localidad, tipo, fecha (actividades con una fecha ese día que todavía tenga lugar),
  rango de precio, calificación mínima y palabra clave (nombre, descripción y emprendimiento, sin importar
  mayúsculas ni tildes). Van en la URL, así una búsqueda se puede compartir.
- Los filtros se aplican en memoria (`CatalogService`). Alcanza para este proyecto; con miles de actividades
  convendría pasarlos a la consulta y paginar.
- **No necesita scripts SQL:** usa `review`, que ya existe en Supabase.

## Detalle de una actividad (CU-06)

- Público. Muestra emprendimiento, descripción, ubicación, duración, precio, tipo, edad mínima, el equipamiento
  que hay que traer y el que pone el prestador, las próximas fechas con su disponibilidad (y si ya cerraron
  las reservas por el límite de minutos previos) y las valoraciones.
- **Clima:** `OpenMeteoWeatherService` usa la API pública de [Open-Meteo](https://open-meteo.com/), sin clave
  (uso no comercial). Como `locality` no tiene coordenadas, primero busca la localidad por nombre en Uruguay y
  después pide el pronóstico de los próximos 16 días (el máximo de la API). Guarda las coordenadas en memoria
  y reutiliza el pronóstico durante una hora.
- Si la API no responde, no encuentra el lugar o la fecha está a más de 16 días, el detalle se muestra sin
  clima (5a). El pronóstico se pide **fuera de la transacción**, para no tener ocupada una conexión de Supabase
  mientras se espera a la API.
- Para reservar hay que ingresar como turista (1a). El botón queda listo para el CU-14.
- En los tests, el clima se reemplaza por uno simulado: no dependen de internet.

## Reservar (CU-14)

- Desde el detalle, el turista elige la fecha y cuántas personas van, ve el resumen con el precio total y
  confirma. La reserva queda **PENDIENTE** (a la espera del pago, CU-15) y **todavía no ocupa cupos**.
- Se verifica que haya cupos suficientes (si no, se informa cuántos quedan, 4a) y que no hayan cerrado las
  reservas por el límite de minutos previos (4b). Se verifica al ver el resumen y otra vez al confirmar.
- El precio total queda guardado en la reserva: si el prestador cambia el precio después, no la afecta (CU-10).
- Sin sesión, "Reservar" pide iniciarla y después **vuelve a la reserva** (1a, `LoginPageController`). Un
  prestador o administrador no puede reservar: `/tourist/**` es solo para turistas (1b).
- **Integrantes:** si van 2 o más personas, aparece un bloque por cada acompañante para cargar nombre, apellido,
  cédula y teléfono (`static/js/reserve-companions.js`). La persona 1 es el turista, que ya está identificado por su
  cuenta. No se puede repetir una cédula en la misma reserva. Se guardan en `reservation_participant`.
- **Necesita el script** `2026-10-26_cu14_cu15_participants_payments.sql` (ver abajo).

## Pagar (CU-15) con Mercado Pago

El pago lo procesa un servicio externo: **Mercado Pago**, en su modo de prueba (*sandbox*), donde no se mueve plata
real. Se usa el *Checkout API* con el formulario de tarjeta de Mercado Pago (*Card Payment Brick*):

1. En la página de pago, el formulario de Mercado Pago (con la **clave pública**) convierte la tarjeta en un *token*.
   El número y el código de seguridad **no pasan por nuestro servidor**.
2. El servidor cobra con ese token (`POST /v1/payments`) usando el **access token**, que es secreto.
3. Solo si Mercado Pago lo **aprueba**, la reserva pasa a **CONFIRMADA** y ocupa los lugares.

Qué pasa en cada caso (`PaymentService`):

| Respuesta | Qué hace la app |
| --- | --- |
| Aprobado | Confirma la reserva, bloqueando la fecha para que dos pagos no ocupen el mismo lugar. Si justo se llenó, **devuelve el dinero** |
| Rechazado (4a) | Sigue PENDIENTE, muestra el motivo (fondos, código de seguridad, ...) y se puede reintentar |
| En revisión | Sigue PENDIENTE; en la reserva aparece "Consultar el estado del pago" |
| Sin respuesta o error del servicio (4b) | Sigue PENDIENTE, no se confirma y se puede reintentar. Antes de reintentar se busca si el intento anterior llegó a cobrarse, para **no cobrar dos veces** |
| Sin lugares (2a), pasaron 24 h o cerraron las reservas (2b) | No se cobra |

Cada intento queda guardado en `reservation_payment`. El monto sale siempre de la reserva, nunca del navegador.

### Cómo probarlo con el sandbox de Mercado Pago

1. Entrá a [Mercado Pago Developers](https://www.mercadopago.com.uy/developers/panel/app) con una cuenta de Mercado
   Pago (sirve la personal) y tocá **Crear aplicación**. Tipo de pago: *pagos online*, producto: *Checkout API* /
   *Checkout Bricks*.
2. En la aplicación, abrí **Credenciales de prueba** y copiá la **Public Key** y el **Access Token** (empiezan con
   `TEST-`).
3. En IntelliJ: **Run → Edit Configurations → Proytic2026Application → Environment variables** y agregá (separadas
   por `;`):
   `MP_PUBLIC_KEY=TEST-...;MP_ACCESS_TOKEN=TEST-...`
   Al arrancar, el log dice `Pagos con Mercado Pago (credenciales de prueba)`.
   **Nunca las pongas en el código ni en el repo**: con el access token se puede cobrar.
4. Reservá una actividad como turista y tocá **Pagar**. Usá una
   [tarjeta de prueba de Uruguay](https://www.mercadopago.com.uy/developers/es/docs/checkout-bricks/additional-content/your-integrations/test/cards)
   (código de seguridad `123`, vencimiento `11/30`) y elegí el resultado con el **nombre del titular**:

   | Titular | Resultado |
   | --- | --- |
   | `APRO` | Aprobado |
   | `OTHE` | Rechazado |
   | `CONT` | Pendiente (en revisión) |
   | `FUND` | Rechazado por fondos insuficientes |
   | `SECU` | Rechazado por código de seguridad |

   Si pide documento, usá cualquier cédula válida. El correo del pagador tiene que ser **distinto** al de tu cuenta de
   Mercado Pago (por defecto se usa el del turista; para cambiarlo, `MP_PAYER_EMAIL=otro@correo.com`).
5. Los pagos de prueba se ven en el panel de Mercado Pago, en la sección de pagos de prueba de la aplicación.

**Sin credenciales** (por ejemplo, en los tests o si no querés crear la cuenta), la app usa un **pago simulado**
que no se conecta a nada: en la página de pago se escribe el titular (`APRO`, `OTHE`, `CONT`, `FUND` o `ERROR`
para simular que el servicio no responde) y responde igual que el sandbox.

- **Necesita el script** `2026-10-26_cu14_cu15_participants_payments.sql`.
- Pendiente para más adelante: pagar con las tarjetas guardadas al registrarse (CU-01). Para eso hay que guardarlas
  como *clientes y tarjetas* de Mercado Pago; hoy se paga ingresando la tarjeta.

## Valorar (CU-18)

Con una reserva **FINALIZADA** (la pasa el CU-16 cuando termina la fecha), el turista ve "Valorar" en la reserva y en
Mis reservas. Elige de 1 a 5 estrellas y, si quiere, escribe un comentario (hasta 1000 caracteres).

- Se publica enseguida en el detalle de la actividad (CU-06) con el nombre y la inicial del apellido. El promedio no se
  guarda aparte: se calcula con las valoraciones, así que la vidriera (CU-05) y el detalle quedan al día solos.
- **3a:** sin puntuación no se guarda y se le pide elegirla (lo que escribió no se pierde).
- **1a:** una sola valoración por reserva. Si ya la valoró, se le muestra la suya y se le explica. Si la columna
  `review.reservation_id` es única en la base (como dice la entidad), también se frena un doble envío simultáneo.
- Una reserva que todavía no terminó o que es de otro turista no se puede valorar (la de otro da 404).
- No necesita scripts nuevos: la tabla `review` ya existía.

## Mis reservas (CU-17)

`/tourist/reservations` (tarjeta "Mis reservas" del espacio del turista) muestra un **calendario del mes** con cada
reserva y su estado en colores: pendiente de pago, confirmada, finalizada, vencida o cancelada.

- **Filtros (paso 3):** el mes (o las flechas ‹ ›), un día (tocándolo en el calendario) y el tipo de actividad (solo
  aparecen los tipos de lo que reservó). Debajo del calendario está la lista del mes o del día elegido.
- **Detalle (paso 4):** cada reserva lleva a `/tourist/reservations/{id}`, que ahora tiene "Mis reservas" para volver.
- **4a:** si está pendiente y todavía se puede pagar, aparece "Pagar" (CU-15).
- **4b:** si está finalizada y no tiene valoración, aparece "Valorar" (CU-18).
- **2a:** sin reservas, se ofrece explorar actividades. Arriba se muestra la próxima reserva pendiente o confirmada.
- Es solo una consulta: no cambia nada. En el celular el calendario muestra puntos y los datos quedan en la lista.

## Actualizar el estado de las reservas (CU-16)

Una tarea programada (`ReservationStatusJob`) revisa las reservas cada 5 minutos (empieza 30 segundos después de arrancar):

| Reserva | Pasa a | Cuándo |
| --- | --- | --- |
| PENDING | EXPIRED | Pasaron 24 horas desde que se creó sin pago aprobado, **o** su fecha ya cerró las reservas por el límite de minutos previos |
| CONFIRMED | FINISHED | Terminó su fecha (inicio + duración de la actividad). Desde ahí se puede valorar (CU-18) |

- **Aviso al turista (paso 4):** cuando una reserva vence se guarda un aviso en `tourist_notification`. El turista lo ve
  arriba en su espacio (`/tourist`), con el enlace a la reserva, hasta que toca "Marcar como vistos".
- **Si paga justo cuando vence (2a):** el vencimiento y la confirmación del pago bloquean la misma fecha y miran el
  estado actual en la base, así que no se pisan. Si ya quedó confirmada, no se vence; si ya venció, el pago no la
  confirma y se devuelve el dinero.
- Las vencidas no ocupaban lugar y las finalizadas lo siguen ocupando (cuentan CONFIRMED y FINISHED).
- Configuración en `application.properties`: `app.reservations.status-job.every-ms` (cada cuánto),
  `app.reservations.status-job.enabled` (apagarla). En los tests está apagada y se llama directo al servicio.
- **Necesita el script** `2026-10-26_cu16_tourist_notifications.sql`.

## Inicio de sesión y permisos (CU-04)

Se usa **Spring Security** (`config/SecurityConfig`). Las reglas del login están en `AuthService`;
`LoginPageController` maneja la sesión y la elección de perfil.

- Un mismo correo puede ser turista, prestador y administrador. Si tiene más de un perfil, después de
  poner bien la contraseña se pregunta con cuál ingresar, y los permisos son los de ese perfil: un
  prestador no puede entrar a las funciones del turista.
- Credenciales incorrectas: mensaje genérico, sin decir si falló el correo o la contraseña.
- Un prestador `REJECTED` no puede ingresar como prestador. Uno `PENDING` entra, pero ve un aviso y todavía
  no puede publicar.
- Rutas públicas: el menú, el login, los registros, los archivos de `static` y `/api/**` (la API de solo
  lectura para otras plataformas, que no usa sesión ni token CSRF). Todo lo demás pide sesión iniciada.
- **Si agregan un endpoint de `/api/**` que modifique datos, hay que protegerlo en `SecurityConfig`:** hoy
  toda la API es pública.
- Los formularios nuevos tienen que usar `th:action` para que Thymeleaf agregue el token CSRF solo.

### Inicio: fotos, mapa y logo

- **Fotos de la portada:** son de Wikimedia Commons y se cargan desde su servidor (`static/css/landing.css`).
  Los créditos están en el pie de página (`templates/fragments/site.html`). Para cambiar una foto, reemplazar
  la URL en `landing.css` y su enlace en el pie.
- **Mapa:** los trazos de los 19 departamentos salen de Natural Earth (dominio público) y están en
  `src/main/resources/map/uruguay-departments.txt`. `HomeService` cuenta las actividades publicadas por
  departamento y cada etiqueta lleva a `/activities?departmentId=...`.
- **Logo de OASIS:** `static/img/oasis-logo.png` (fondo transparente), `oasis-logo-light.png` (para fondos
  oscuros), `oasis-mark.png` (solo el sol, en el encabezado) y `favicon.png`.

## Scripts SQL de Supabase

Los cambios de tablas están en `db/supabase/`, en orden por fecha. Cuando entra uno nuevo a
`main`, alguien del equipo lo corre una vez en el SQL Editor de Supabase, **antes** de arrancar
la app con el perfil `postgres`. Los scripts solo agregan tablas o columnas: no borran datos.

| Script | Para qué |
| --- | --- |
| `2026-10-18_cu02_provider_address.sql` | Domicilio y nombre del emprendimiento del prestador (CU-02) |
| `2026-10-22_cu01_payment_method.sql` | Tabla `payment_method`: medio de pago opcional del turista (CU-01) |
| `2026-10-22_cu01_payment_method_existing_table.sql` | Ajuste de `payment_method` si ya existía sin vencimiento (ya corrido en la base del equipo) |
| `2026-10-24_localities.sql` | 122 localidades de los 19 departamentos (ciudades y destinos turísticos). Se puede correr más de una vez |
| `2026-10-26_cu14_cu15_participants_payments.sql` | Tablas `reservation_participant` (acompañantes de una reserva) y `reservation_payment` (intentos de pago). **Sin este script la app no arranca con Supabase** |
| `2026-10-26_cu10_cancel_activity.sql` | Agrega el estado `CANCELLED` a `reservation.status` (CU-10). Sin él, dar de baja una actividad con reservas da error |
| `2026-10-26_cu16_tourist_notifications.sql` | Tabla `tourist_notification`: avisos al turista, como las reservas vencidas (CU-16). **Sin este script la app no arranca con Supabase** |

## Medios de pago

El turista puede registrar una tarjeta al crear su cuenta (CU-01, opcional). **Nunca se guarda el
número ni el código de seguridad**: el `PaymentTokenizer` los convierte en un token y se guardan solo
el token, la marca, los últimos 4 dígitos y el vencimiento. Hoy el token lo genera
`SimulatedPaymentTokenizer`; cuando se integre el servicio de pagos externo (CU-15) se reemplaza esa
clase por una que llame a su API. Para probar sirven las tarjetas de prueba `4242 4242 4242 4242`
(Visa) o `5555 5555 5555 4444` (Mastercard), con cualquier vencimiento futuro y CVV.

## Reglas importantes

- **Nunca subas contraseñas, tokens ni claves al repo.** El repo es público y los bots las encuentran en minutos. Si pasa, hay que cambiar la contraseña en Supabase de inmediato: borrar el commit no alcanza.
- **Las tablas de Supabase se cambian con scripts SQL**, no desde Spring. El perfil `postgres` usa `ddl-auto=validate`: si una entidad no coincide con la tabla, la app no arranca y avisa qué falta.
- Los cambios entran a `main` por **pull request**, no con push directo.
- Los tests usan H2, así que no tocan la base compartida: `./mvnw test`.
