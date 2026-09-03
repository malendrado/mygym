# mygym

Monorepo: backend REST (`api/`) + app instalable web/Android/iOS (`app/`).

## Estructura

```
mygym/
├── api/   Java 21 + Spring Boot 3.5 + Maven (controllers → services → repositories → models)
└── app/   Angular standalone + Ionic Angular + Capacitor
```

Sin harness `ai-dev-process`: no hay gates de CI/CD configurados todavía, solo el scaffold de cada proyecto.

## api/

Scaffold de infraestructura únicamente: health check (Actuator, con probes de liveness/readiness),
manejo global de errores (`ProblemDetail`) y Swagger/OpenAPI. Sin base de datos ni entidades de
negocio — las carpetas `controllers/`, `services/`, `repositories/` y `models/` están vacías,
listas para el primer dominio real.

```bash
cd api
mvn spring-boot:run          # http://localhost:8080
```

- Health: `GET /actuator/health`
- Swagger UI: `GET /swagger-ui.html`
- OpenAPI JSON: `GET /v3/api-docs`

Requiere Java 21 (`JAVA_HOME` apuntando a un JDK 21).

## app/

Angular standalone (signals, `@if`/`@for`) envuelto con Ionic Angular para UI mobile y Capacitor
para empaquetado nativo. Todavía sin plataformas nativas agregadas (`android/`, `ios/`) porque no
hay Android Studio / Xcode instalados en este entorno.

```bash
cd app
npm start                    # ng serve, http://localhost:4200 (proxyea /actuator hacia la api en :8080)
npm run build                # build web de producción
npx cap sync                 # cuando se agreguen plataformas nativas
```

Cuando estén disponibles Android Studio / Xcode:

```bash
npx cap add android
npx cap add ios
```

La página de inicio (`src/app/pages/home`) incluye un botón que llama a `GET /actuator/health` de
la api, para verificar que ambos proyectos quedan conectados.
