# Arquitectura — mygym

SaaS multi-tenant para gimnasios: reserva de clases por bloques horarios, panel
de administración por gimnasio y panel super-admin para gestionar la cartera
de gimnasios. Monorepo con dos proyectos independientes que se despliegan por
separado.

```
mygym/
├── api/    Java 21 + Spring Boot 3.5 + Maven — REST API
└── app/    Angular 22 (standalone) + Ionic 9 — dos builds del mismo código:
            app móvil (Capacitor) y sitio web (mygym.cl)
```

## Multi-tenancy

Shared schema: una sola base Postgres, todas las tablas tenant-scoped llevan
`gym_id` (columna `BIGINT` plana, sin `@ManyToOne` en las entidades JPA — se
resuelve por id explícito en cada query/repositorio). No hay schema-per-tenant
ni DB-per-tenant. El aislamiento entre gimnasios se garantiza en la capa de
servicio (cada método de `GymAdminController`/`MemberController` resuelve el
`gymId` **desde el JWT del caller**, nunca desde un parámetro de la URL), no
en la base de datos.

## Roles y autenticación

Tres roles (`Role.java`): `SUPER_ADMIN` (sin `gym_id`, gestiona la cartera de
gimnasios), `GYM_ADMIN` (uno o más por gimnasio, administra su propio
gimnasio) y `MEMBER` (socio de un gimnasio, reserva clases).

- Login con Google (`@abacritt/angularx-social-login` en el front,
  `GoogleTokenVerifier` en el back) intercambiado por un JWT propio (HS256,
  `JwtService`, secreto en `APP_JWT_SECRET`).
- `AuthenticatedUser.from(jwt)` en el back y `AuthUser`/`LoginResponse` en el
  front tratan `gymId` como nullable (solo los super-admin no lo tienen).
- No hay autoregistro de roles admin: un `GYM_ADMIN` se crea junto con su
  gimnasio (`GymCreateRequest.ownerName/ownerEmail`) o se agrega después desde
  el panel super-admin (`POST /api/gyms/{id}/admins`); su cuenta de Google
  recién se vincula (`googleSub`) en el primer login.
- Endpoints por rol: `/api/gyms/**` → `SUPER_ADMIN` únicamente; el panel de
  cada dueño usa un espejo separado, `/api/gym-admin/**`
  (`GymAdminController`, `MemberController`), que resuelve `gymId` del JWT en
  vez de un `{id}` de la URL — evita IDOR (un `GYM_ADMIN` no puede tocar otro
  gimnasio con solo cambiar un id).

## Dominio (backend)

Paquetes planos por capa: `controllers` → `services` → `repositories` →
`models` (+ `models.dto` para records de entrada/salida, `services.exception`
para excepciones de negocio). Sin `@ManyToOne` entre agregados — relaciones
por id plano (`gymId`, `gymBlockId`, `memberId`).

Entidades principales (ver migraciones Flyway en
`api/src/main/resources/db/migration/`):

- **Gym** — un gimnasio: nombre, slug único, `max_users`, `google_login_enabled`,
  `theme_color` + `logo_svg` (personalización, `V5`).
- **GymBlock** — un bloque de clase recurrente semanal (día, hora inicio/fin,
  capacidad).
- **AppUser** — usuario (cualquier rol), único por email, `gym_id` nullable
  (`V3`, para permitir `SUPER_ADMIN`).
- **Reservation** — reserva de un socio a una ocurrencia concreta
  (`gym_block_id` + `class_date`), única por slot (`UNIQUE(gym_block_id,
  member_id, class_date)`).

Row-Level Security habilitada (`V4`) en las 4 tablas para cerrar la API REST
auto-generada de Supabase (PostgREST) — el backend nunca la usa, se conecta
directo por JDBC como rol dueño de las tablas (bypassea RLS), así que esto no
afecta a la app.

## Frontend: un solo código fuente, dos builds

`app/angular.json` define dos `projects` sobre el mismo `sourceRoot` (`src/`):

| Proyecto  | Entry point         | Rutas              | Público                                   |
|-----------|----------------------|--------------------|--------------------------------------------|
| `app`     | `src/main.ts`         | `app.routes.ts`     | Socios — app instalable (Capacitor)        |
| `landing` | `src/main.landing.ts` | `web.routes.ts`     | Marketing + `GYM_ADMIN` + `SUPER_ADMIN` (mygym.cl) |

Decisión de producto: la app móvil es solo para socios; toda la
administración vive en la web, siempre detrás de login de Google. Mover
"admin a la web" no movió páginas de carpeta — se agregaron `web.routes.ts` /
`web.config.ts` y `main.landing.ts` pasó de bootstrapear solo el componente
`Landing` (sin Router) a bootstrapear el shell completo `App`
(`<ion-app><ion-router-outlet>`).

Estructura de `app/src/app/`:

- `core/` — servicios transversales (`auth`, `gym`, `member`, `reservation`),
  guards (`authGuard`, `roleGuard('ROLE')`), interceptors (`error.interceptor`
  traduce cualquier error HTTP a un `Error` con `.message` ya listo para
  mostrar), providers (`provideGoogleAuth()`).
- `pages/` — una carpeta por página/dominio (`login`, `landing`, `join`,
  `member`, `gym-admin`, `admin/gyms/*`), sin capa `features/` intermedia.

## Deploy de la landing (Vercel) — carpeta separada del build

`app/vercel-landing/` vive fuera de `dist/` (no se pisa en cada build) y
contiene `vercel.json` (`outputDirectory: public`, rewrite SPA), `api/contact.js`
(función serverless que llama a la API de Resend para el formulario de
contacto) y `public/` (el build copiado ahí antes de cada deploy —
gitignoreado, se regenera a mano).

Checklist de deploy:

```bash
cd app
ng build landing
rm -rf vercel-landing/public/* && cp -r dist/landing/browser/* vercel-landing/public/
cd vercel-landing && vercel --prod --yes
```

## Infraestructura y entornos

| Pieza                | Dónde                                                          |
|----------------------|-----------------------------------------------------------------|
| API                  | Railway (`mygym-api`, build Railpack/Maven), `https://api-production-be95.up.railway.app` |
| Base de datos        | Supabase Postgres (**Session pooler**, no conexión directa — Railway no tiene salida IPv6) |
| Frontend web         | Vercel (`vercel-landing/`), dominio `mygym.cl`/`www.mygym.cl` (DNS en cPanel, no en Vercel) |
| Emails               | Resend (`onboarding@resend.dev` en desarrollo, `notificaciones@mygym.cl` verificado en prod) |
| Sugerencia de marca  | Google Gemini (API key gratuita), no Anthropic |

Variables de entorno requeridas por el backend: `SUPABASE_DB_URL` (con
`?prepareThreshold=0`, necesario por el pooler de Supabase),
`SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`, `APP_JWT_SECRET`,
`GOOGLE_CLIENT_ID`, opcionales `GEMINI_API_KEY`/`GEMINI_MODEL`,
`RESEND_API_KEY`/`RESEND_FROM`, `APP_CORS_ALLOWED_ORIGINS`. Sin las
obligatorias, el contexto de Spring no levanta ni para tests que no tocan esos
módulos (se resuelven vía `@Value` al construir los beans).

## Dónde está el detalle día a día

Este documento es el mapa de arquitectura. Las decisiones puntuales, gotchas
de Ionic/Playwright/Vercel/Supabase y el historial de "qué se hizo y por qué"
en cada sesión viven en `.claude/skills/mygym/SKILL.md` — se actualiza cada
vez que se resuelve algo no trivial, con fecha.
