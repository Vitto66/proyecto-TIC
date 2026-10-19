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

## Páginas y endpoints

| Qué | Dónde |
| --- | --- |
| Formulario de registro de turistas (CU-01) | http://localhost:8080/register/tourist |
| Formulario de registro de prestadores (CU-02) | http://localhost:8080/register/provider |
| API: registrar turista (CU-01) | `POST /api/auth/register/tourist` |
| API: registrar prestador (CU-02) | `POST /api/auth/register/provider` |

Las páginas usan Thymeleaf + Bootstrap: las plantillas están en `src/main/resources/templates`
y los estilos y scripts en `src/main/resources/static`.

## Scripts SQL de Supabase

Los cambios de tablas están en `db/supabase/`, en orden por fecha. Cuando entra uno nuevo a
`main`, alguien del equipo lo corre una vez en el SQL Editor de Supabase.

## Reglas importantes

- **Nunca subas contraseñas, tokens ni claves al repo.** El repo es público y los bots las encuentran en minutos. Si pasa, hay que cambiar la contraseña en Supabase de inmediato: borrar el commit no alcanza.
- **Las tablas de Supabase se cambian con scripts SQL**, no desde Spring. El perfil `postgres` usa `ddl-auto=validate`: si una entidad no coincide con la tabla, la app no arranca y avisa qué falta.
- Los cambios entran a `main` por **pull request**, no con push directo.
- Los tests usan H2, así que no tocan la base compartida: `./mvnw test`.
