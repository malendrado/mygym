# mygym

SaaS multi-tenant para gimnasios: reserva de clases, panel de administración
por gimnasio y panel super-admin. Monorepo: backend REST (`api/`) + app
instalable web/Android/iOS (`app/`).

Ver [ARCHITECTURE.md](./ARCHITECTURE.md) para el detalle de arquitectura
(multi-tenancy, roles, dominio, builds del frontend, infraestructura). El
historial de decisiones y gotchas del día a día vive en
`.claude/skills/mygym/SKILL.md`.

## Estructura

```
mygym/
├── api/   Java 21 + Spring Boot 3.5 + Maven (controllers → services → repositories → models)
└── app/   Angular standalone + Ionic Angular + Capacitor
```

## api/

```bash
cd api
mvn spring-boot:run          # http://localhost:8080
```

Requiere Java 21 (`JAVA_HOME` apuntando a un JDK 21) y las variables de
entorno `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`,
`APP_JWT_SECRET`, `GOOGLE_CLIENT_ID` (ver ARCHITECTURE.md para la lista
completa) — sin ellas el arranque falla al no poder resolver el datasource.

- Health: `GET /actuator/health`
- Swagger UI: `GET /swagger-ui.html`
- OpenAPI JSON: `GET /v3/api-docs`

## app/

Angular standalone (signals, `@if`/`@for`) envuelto con Ionic Angular para UI
mobile y Capacitor para empaquetado nativo. Dos builds sobre el mismo código
fuente (ver ARCHITECTURE.md): `app` (socios, móvil) y `landing` (marketing +
paneles de administración, mygym.cl).

```bash
cd app
npm start                    # ng serve, http://localhost:4200 (proxyea /api hacia la api en :8080)
npm run build                # build web de producción del proyecto "app"
ng build landing             # build de la landing/paneles de administración
npx cap sync                 # cuando se agreguen plataformas nativas
```

Cuando estén disponibles Android Studio / Xcode:

```bash
npx cap add android
npx cap add ios
```
