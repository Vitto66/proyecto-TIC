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
| Iniciar sesión (CU-04) | http://localhost:8080/login |
| Elegir perfil, si el correo tiene más de uno (CU-04) | http://localhost:8080/login/profile |
| Espacio del turista / prestador / administrador | `/tourist`, `/provider`, `/admin` (solo con sesión y ese perfil) |
| Formulario de registro de turistas (CU-01) | http://localhost:8080/register/tourist |
| Formulario de registro de prestadores (CU-02) | http://localhost:8080/register/provider |
| Revisar prestadores pendientes (CU-03, administrador) | http://localhost:8080/admin/providers |
| Registrar / ver mi emprendimiento (CU-07, prestador aprobado) | http://localhost:8080/provider/business |
| Mis actividades y publicar una nueva (CU-08, con emprendimiento) | http://localhost:8080/provider/activities |
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

### Fondos del menú inicial

Los tres paisajes son ilustraciones propias en `static/img/landscape-*.svg`. Para usar fotos, copiarlas a
`static/img/` y cambiar las tres líneas `.hero-slide[data-scene=...]` de `static/css/home.css`.

## Scripts SQL de Supabase

Los cambios de tablas están en `db/supabase/`, en orden por fecha. Cuando entra uno nuevo a
`main`, alguien del equipo lo corre una vez en el SQL Editor de Supabase, **antes** de arrancar
la app con el perfil `postgres`. Los scripts solo agregan tablas o columnas: no borran datos.

| Script | Para qué |
| --- | --- |
| `2026-10-18_cu02_provider_address.sql` | Domicilio y nombre del emprendimiento del prestador (CU-02) |
| `2026-10-22_cu01_payment_method.sql` | Tabla `payment_method`: medio de pago opcional del turista (CU-01) |
| `2026-10-22_cu01_payment_method_existing_table.sql` | Ajuste de `payment_method` si ya existía sin vencimiento (ya corrido en la base del equipo) |

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
