# mygym — arquitectura y guía de portabilidad (cambiar de servidor o de base de datos)

Última actualización: 2026-10-22. Sirve para mudar la API a otro servidor, la base de datos a otro Postgres o el front a otro hosting sin depender de la memoria de nadie. **No contiene secretos**: solo nombres de variables. Los valores viven en el gestor de contraseñas del dueño y en el panel de cada proveedor.

## 1. Mapa de piezas

| Pieza | Hoy | Qué hace | ¿Atada al proveedor? |
|---|---|---|---|
| API | Railway (servicio `mygym-api`, build Railpack/Maven, `https://api-production-be95.up.railway.app`) | Spring Boot 3.5 / Java 21, un solo jar | No: cualquier host que corra Java 21 |
| Base de datos | Supabase Postgres, por el **pooler** (puerto 6543, modo transacción) | Todo el estado; Flyway crea el esquema | Solo por el pooler y por RLS (ver 3) |
| Front | Vercel (`app/vercel-landing/`), dominios `mygym.cl` y `www.mygym.cl` | Angular 22 + Ionic 9, PWA, estático + una función `api/contact.js` (formulario de contacto, usa Resend) | No: es estático |
| DNS | cPanel (no Vercel) | `mygym.cl`, registros de Vercel y de Resend | — |
| Correo | Resend, remitente `notificaciones@mygym.cl` (dominio verificado) | Todos los avisos y el código de la firma | Cambiable por variable, pero hay que verificar el dominio |
| Pagos | Flow.cl: cuenta de mygym (suscripción de los gyms) y, a futuro, una cuenta por gym (llaves cifradas en la BD) | Cobro con tarjeta o Webpay | Las URLs de retorno y de aviso apuntan a la API |
| Login | Google Identity (GSI), `GOOGLE_CLIENT_ID` público | Ingreso de socios y admins | Hay que autorizar el origen nuevo en la consola de Google |
| Push | Web Push con llaves VAPID propias | Avisos a socios y equipo | No |
| IA | Gemini (clave gratuita) | Sugerencia de colores de marca | Opcional |

Todo se guarda en la base de datos: **no hay almacenamiento de archivos externo**. Logos (SVG saneado) y fotos van como texto en columnas `TEXT` (data URI o base64).

## 2. Variables de entorno de la API (solo nombres)

Obligatorias: `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`, `APP_JWT_SECRET`, `GOOGLE_CLIENT_ID`, `APP_ENCRYPTION_KEY` (32 bytes en base64, 44 caracteres terminados en `=`: si el formato está mal, **la app no arranca**).
Opcionales con valor por defecto: `PORT` (8080), `DB_POOL_SIZE` (10), `APP_CORS_ALLOWED_ORIGINS` (`https://www.mygym.cl,https://mygym.cl`), `RESEND_API_KEY`, `RESEND_FROM`, `GEMINI_API_KEY`, `GEMINI_MODEL`, `PUSH_VAPID_PUBLIC_KEY`, `PUSH_VAPID_PRIVATE_KEY`, `PUSH_VAPID_SUBJECT`, `MYGYM_CONTACT_WHATSAPP`, `MYGYM_CONTACT_EMAIL`, `SPRINGDOC_ENABLED` (false).
Flow: `FLOW_BASE_URL`, `FLOW_RETURN_URL`, `FLOW_MEMBER_RETURN_URL`, `FLOW_WEBHOOK_URL`, y las de la cuenta propia: `MYGYM_FLOW_API_KEY`, `MYGYM_FLOW_SECRET_KEY`, `MYGYM_FLOW_RETURN_URL`, `MYGYM_FLOW_MEMBER_RETURN_URL`, `MYGYM_FLOW_WEBHOOK_URL`, más los datos bancarios para transferencias `MYGYM_BANK_*`.
**Ojo:** las URLs de retorno y de aviso de Flow traen por defecto el dominio de Railway escrito en `application.yml`. Al mudar la API hay que fijarlas por variable (o cambiar el defecto).

## 3. Base de datos

- **Motor:** PostgreSQL estándar. No usa extensiones, `pg_cron`, `pgcrypto` ni funciones propias de Supabase. Sí usa: `JSONB` (memoria viva), índices parciales (8 migraciones), un trigger (constancia de firma inmutable, V58), `SELECT … FOR UPDATE` / `FOR NO KEY UPDATE` (cupos y pagos) y `UUID`.
- **Esquema:** lo crea Flyway al arrancar desde `api/src/main/resources/db/migration` (V1 a V58). `ddl-auto: validate`: si el esquema no coincide con las entidades, la app no levanta. **Flyway es la única fuente de verdad**; nunca cambiar tablas a mano.
- **Constancia de firma inmutable:** `gym_contract_acceptance` tiene triggers que impiden `UPDATE`, `DELETE` y `TRUNCATE` (V58 y V59; sin V59 un `TRUNCATE` la vaciaba, se comprobó en un Postgres limpio). No protege contra quien tenga la clave de la base y pueda borrar el trigger; la prueba ante terceros es además el correo de constancia que recibe el firmante.
- **RLS:** toda tabla nueva debe llevar `ENABLE ROW LEVEL SECURITY` (lo exige `MigrationRlsTest`). No hay políticas: la API entra como dueño de la base y controla el acceso en Java. Si se muda a un Postgres sin la API REST de Supabase, el RLS es inofensivo y se puede dejar.
- **Conexión (lo más delicado):** hoy es el **pooler de Supabase en modo transacción**. Consecuencias:
  - La URL JDBC lleva `?prepareThreshold=0` (el pooler rompe los prepared statements).
  - El host directo de Supabase es solo IPv6 y Railway no sale por IPv6: por eso se usa el pooler.
  - Hikari: `maximum-pool-size` 10 (variable `DB_POOL_SIZE`), `connection-timeout` 5 s. Con varias instancias, `instancias × pool` debe quedar bajo el tope del pooler (por defecto 15).
  - Un Postgres propio o RDS con conexión directa no necesita `prepareThreshold=0` ni el pooler.
  - `ALTER TABLE flyway_schema_history ENABLE ROW LEVEL SECURITY` **cuelga el deploy**: no incluirlo jamás en una migración.
- **Cifrado en la BD:** solo `gym.flow_api_key` y `gym.flow_secret_key` (AES-256-GCM con `APP_ENCRYPTION_KEY`). Hoy ningún gym tiene llaves cargadas. Si se rota la clave con llaves cargadas, hay que volver a cifrarlas o vaciarlas (si no, cargar el gym falla).
- **Datos sensibles:** nombres, correos, RUT, teléfonos y contacto de emergencia de socios (Ley 19.628, y 21.719 desde 2026-12-01); constancias de firma del contrato (`gym_contract_acceptance`, inmutable, sin llave foránea al gym a propósito).

### Mudar la base de datos a otro Postgres
1. Crear el Postgres destino (misma versión mayor o superior; 15+ recomendado) y un usuario dueño del esquema.
2. Poner la API en modo mantenimiento corto (o apagarla). Hacer `pg_dump --format=custom --no-owner --no-privileges` desde Supabase por la conexión directa o por el pooler en modo sesión (puerto 5432), no por el de transacción.
3. `pg_restore --no-owner` al destino. **Incluye `flyway_schema_history`**: así Flyway ve que ya está en V58 y no reaplica nada. Comprobar `select max(installed_rank), version from flyway_schema_history`.
4. Cambiar `SUPABASE_DB_URL`, `SUPABASE_DB_USER` y `SUPABASE_DB_PASSWORD` en el servidor de la API (y `?prepareThreshold=0` solo si el destino usa un pooler en modo transacción). Reiniciar.
5. Verificar: arranca sin errores de Flyway ni de validación, `GET /actuator/health` da 200, se puede iniciar sesión, un socio ve su agenda.
6. Mantener la base vieja intacta una semana como respaldo antes de borrarla.
Tiempo estimado con unas decenas de MB de datos: unos 30 minutos, casi todos de mantenimiento.

## 4. API (servidor)

- **Build:** `cd api && mvn -DskipTests package` produce un jar ejecutable (Spring Boot). Java **21**. Arranque: `java -jar target/*.jar`. Puerto por `PORT`. Salud: `/actuator/health` y `/actuator/info` (lo único expuesto de Actuator). Swagger apagado por defecto.
- **Despliegue actual en Railway:** manual, `cd api && npx -p @railway/cli railway up --ci`. Railway **no despliega solo al hacer push**. Tras desplegar, revisar `railway logs` buscando Flyway y `Started MygymApi`.
- **Un solo proceso:** hay estado en memoria y tareas programadas sin bloqueo distribuido. **No correr dos instancias** sin antes resolver esto:
  - Límite de intentos de login (`AuthRateLimitFilter`), caché de estado del gym, de sesiones y de pausa, y caché de cupos: en memoria por proceso.
  - `@Scheduled` sin ShedLock: dos instancias duplicarían correos, avisos y cobros. Tareas: `ClassReminderJob` (cada 5 min), `PlanChangeJob` (cada 5 min), `WaitlistService` (cada 1 min), `GymSubscriptionJob` y `MembershipReminderJob` (09:00 America/Santiago), `ExpiredRowsPurgeJob` (04:30 y 04:45).
- **Detrás de un proxy:** el límite de intentos usa la **última** entrada de `X-Forwarded-For` (la que agrega el proxy de confianza). Con otro proxy o sin él, revisar `AuthRateLimitFilter.clientIp`.
- **Salida a internet que debe estar permitida:** Resend, Flow (`www.flow.cl` / sandbox), Gemini y los servicios push de cada navegador (lista blanca en `PushEndpointPolicy`).
- **Tomcat:** 25 hilos; JSON comprimido.
- **Zona horaria:** el negocio se calcula en `America/Santiago` (la misma hora de Valparaíso).

### Mudar la API a otro servidor
1. Preparar el host (Java 21, o un contenedor con el jar) y cargar **todas** las variables de la sección 2.
2. Fijar `FLOW_*` y `MYGYM_FLOW_*` con las URLs nuevas de la API (retorno y aviso).
3. Autorizar el origen en la consola de Google si el front cambia de dominio, y poner `APP_CORS_ALLOWED_ORIGINS` con los dominios del front.
4. **Front:** cambiar `apiUrl` en `app/src/environments/environment.ts` (hoy escrito a fuego) **y** el dominio de la API en `connect-src` de `app/vercel-landing/vercel.json` (si no, la CSP bloquea las llamadas). Recompilar y desplegar el front.
5. Probar con la URL nueva: salud, login, un pago de prueba de Flow (el aviso de Flow debe llegar al dominio nuevo) y un correo.
6. Cambiar el DNS o el front a la API nueva y dejar la vieja viva un día.

## 5. Front

- Estático: `app/` se compila con `ng build landing` (`dist/landing/browser`). El deploy a Vercel va **siempre por `app/vercel-landing/deploy.sh`** (compila, regenera `public/`, revisa que la CSP incluya el hash del script en línea de Angular, publica y verifica). Nunca `vercel --prod` a mano.
- `vercel.json` define reescrituras (SPA), cabeceras de seguridad, **CSP** y caché (`immutable` solo para archivos con hash; el service worker queda fuera).
- Es una PWA con service worker de Angular. Si se cambia de hosting, hay que reproducir: reescritura de todas las rutas a `index.html`, `/brochure` aparte, las cabeceras y la función `api/contact.js` (o reemplazar el formulario).
- `GOOGLE_CLIENT_ID` y la URL de la API están en `app/src/environments/environment.ts`.

## 6. Servicios externos: qué reconfigurar al mudar

| Si cambia… | Reconfigurar |
|---|---|
| Dominio del front | DNS en cPanel; origen autorizado en Google; `APP_CORS_ALLOWED_ORIGINS`; `www.mygym.cl` en `FLOW_*_URL` (retorno a la app); CSP |
| Dominio de la API | `environment.ts`; `connect-src` en `vercel.json`; URLs de Flow; origen en Google no (solo el del front) |
| Correo | Verificar el dominio en el proveedor nuevo (SPF/DKIM en cPanel); `RESEND_FROM` |
| Pagos | Flow aviso y retorno: se mandan en cada cobro, así que basta cambiar las variables |

## 7. Seguridad operativa (estado al 2026-10-22)

- Railway informó que sus variables de entorno pudieron ser accedidas (incidente de 2026-10-21). Rotadas: `APP_JWT_SECRET`, `APP_ENCRYPTION_KEY` y el par VAPID. **Pendientes:** clave de la base de datos, llaves Flow de mygym, `RESEND_API_KEY`, `GEMINI_API_KEY`. Ver la memoria del proyecto.
- Ningún secreto va en el repositorio. `.gitignore` bloquea `.env*`, `*.pem` y `*.key`.
- Antes de todo despliegue: auditoría de seguridad y de rendimiento sobre el diff, y después una pasada ofensiva liviana a producción.

## 8. Lista de verificación después de cualquier mudanza

- [ ] `GET /actuator/health` responde 200 y los logs muestran Flyway sin errores.
- [ ] Iniciar sesión (correo y Google) como socio, admin de gym y super admin.
- [ ] Reservar y cancelar una clase; el check-in por QR; la pantalla de TV.
- [ ] Pagar un mes de prueba por Flow y confirmar que el aviso llega y acredita una sola vez.
- [ ] Firmar el contrato con el código al correo.
- [ ] Llega un correo (cualquier aviso) y una notificación push.
- [ ] Las cabeceras de seguridad y la CSP salen en el front (`curl -I`), sin violaciones en la consola.

## 9. Plan de recuperación si Railway deja de estar disponible

**Dónde corre hoy (23-oct-2026):** la API en Railway, región **us-west2 (California), 1 réplica** (antes iad/Virginia); la base de Supabase está en AWS us-west-2 (Oregon). Debe haber UNA sola réplica: las tareas programadas, el límite de peticiones y las cachés viven en memoria. Ojo con : `railway scale us-west=1 us-east=0` dejó 2 réplicas (Virginia seguía en 1 porque "us-east" no es la región `iad`); se corrige con `railway scale iad=0 us-west=1` y se confirma con `railway status --json` (multiRegionConfig y numReplicas). `api/railway.toml` declara el chequeo de salud (`/actuator/health`).

Supabase solo aloja la base de datos: no corre aplicaciones Java. Cualquier servidor con Java 21 e internet sirve para la API, y mudarla **no toca los datos**. Opciones (verificar precios y regiones al momento de elegir; todas cuestan pocos dólares al mes para una API chica):

| Opción | Despliegue | Notas |
|---|---|---|
| Fly.io | Contenedor Docker | Tiene región en Santiago; una máquina siempre encendida |
| Render | Contenedor Docker desde GitHub | Parecido a Railway; el plan barato se duerme |
| Google Cloud Run | Contenedor | Robusto; el arranque en frío de Java es lento, fijar instancia mínima |
| VPS (Hetzner, DigitalOcean, Vultr) | Docker + Caddy (HTTPS) | El más barato y con control total; hay que operarlo |
| Koyeb / Northflank | Contenedor | Similares a Render |

**Preparación (hacerla antes de necesitarla):**
1. Un dominio propio para la API (por ejemplo `api.mygym.cl`, CNAME hacia el proveedor). Hoy el front, la CSP y las URLs de Flow llevan el dominio de Railway: con dominio propio, mudar se reduce a cambiar un registro DNS, sin recompilar el front. Pasos: DNS en cPanel, `apiUrl` en `environment.ts`, `connect-src` en `vercel.json`, `FLOW_*_URL`/`MYGYM_FLOW_*_URL`, `APP_CORS_ALLOWED_ORIGINS` (el origen sigue siendo el del front).
2. El `Dockerfile` de `api/` (creado 2026-10-22, con `.dockerignore`): dos etapas, compila con Maven + Java 21 sin tests y ejecuta con JRE 21 como usuario sin privilegios; respeta `PORT`; `JAVA_OPTS` por defecto usa el 75 % de la memoria. Railway no lo necesita (compila solo) y no lo usa. **Estado de la prueba (2026-10-22): PROBADO.** La imagen se construye (`docker build -t mygym-api api/`, unos 4 minutos la primera vez por las dependencias de Maven), corre como usuario sin privilegios, toma `PORT` y `JAVA_OPTS`, y arrancó contra un **Postgres 16 limpio**: aplicó las 58 migraciones de entonces, quedaron 29 tablas todas con RLS y `/actuator/health` respondió `UP`. Con V59 son 59 migraciones. Así queda comprobado que la base no depende de nada propio de Supabase.
3. Las variables de entorno respaldadas en el gestor de contraseñas (los nombres están en la sección 2) y un respaldo periódico de la base con `pg_dump`.
4. Una cuenta ya creada en el proveedor de reserva.

**Ejecución (con lo anterior listo, 30 a 60 minutos; sin preparación, medio día):** crear el servicio, cargar las variables, desplegar, probar salud, login y un pago de prueba de Flow, y recién entonces cambiar el DNS de `api.mygym.cl`. Dejar el servicio viejo vivo un día.
