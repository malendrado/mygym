# Notificaciones push al socio (app instalada)

Estado: IMPLEMENTADO en local el 2026-10-13 (fases 1–5 juntas), probado, SIN commit y SIN deploy. Para desplegar faltan las
variables VAPID en Railway (sección "Claves VAPID y configuración") y correr V45. Notas de lo implementado: la configuración vive en `app.push.vapid.*`
(no `push.vapid.*`); el cifrado y la firma usan solo el JDK (sin Nimbus ni librerías). El plan original decía:
nada existía; sí existía el service worker de Angular (`provideServiceWorker` en `app/src/app/web.config.ts`,
`ngsw-config.json`), que es lo que va a recibir los avisos.

## Objetivo
Que el socio que tiene la app instalada reciba en el teléfono los mismos avisos que hoy le llegan por correo (y uno
nuevo), sin depender de abrir el correo; y que el admin y los profesores del gimnasio se enteren al instante cuando un
socio cancela. El correo se mantiene tal cual: push es un canal adicional, nunca un reemplazo.

## Alcance v1 — avisos al socio

| # | Evento | Cuándo se dispara hoy | Push (título · cuerpo) | Al tocarlo abre |
|---|---|---|---|---|
| 1 | **Se liberó un cupo (lista de espera)** | `WaitlistService.notifyEntry` (cabeza de lista y "resto de la lista") | `¡Se liberó un cupo!` · `Funcional · mié 15 a las 19:00. Tienes 30 min de ventaja.` / `Todavía hay cupo en Funcional · mié 15 19:00. El primero que reserve se lo queda.` | `/member` |
| 2 | **Recordatorio de clase** (NUEVO, no existe por correo) | Cron nuevo: 60 min antes; si reservó con menos de 60 min, a los 30 min; con menos de 30, no se manda | `Tu clase empieza en 1 hora` (o `en 30 minutos`) · `Funcional a las 19:00 en XaloGym` | `/member` |
| 3 | **Cierre de emergencia** | `ClosureNotificationService.sendCancellationEmails` | `XaloGym canceló tu clase` · `Corte de luz: Funcional mié 15 19:00. Tu cupo ya se devolvió.` | `/member` |
| 4 | **Membresía** | `MembershipReminderJob.processMember` (vence en 3/2/1 días y "venció hoy") y `ReservationService.notifyIfClassesExhausted` | `Tu plan vence en 2 días` · `Renueva para seguir reservando.` / `Tu membresía venció` / `Usaste todas tus clases` | `/member` |

## Alcance v1 — aviso al equipo del gimnasio

| # | Evento | A quién | Push (título · cuerpo) | Al tocarlo abre |
|---|---|---|---|---|
| 5 | **Un socio canceló su reserva** (pedido del usuario, 2026-10-13) | Todos los `GYM_ADMIN` y `PROFESOR` activos del gimnasio que tengan los avisos activados | `Cancelación · Funcional 19:00` · `Ana Soto canceló (mié 15). Quedan 3 cupos · 2 en espera.` | `/gym-admin` (Clases → Agenda, ese día) |

- Solo cuando **el socio cancela por su cuenta** (`ReservationService.cancel`). No cuando el admin cancela por él
  (`cancelAsAdmin`: lo hizo el mismo equipo), ni por un cierre de emergencia, ni cuando el sistema quita clases fijas
  (`FixedClassService`).
- **A todos los profesores del gimnasio**, no solo al de esa clase: hoy el bloque guarda el profesor solo como texto
  (`GymBlock.instructorName`), sin vínculo a un usuario `PROFESOR`. Mejora futura (fuera de v1): columna
  `gym_block.instructor_user_id` para avisar solo al profesor de la clase.
- **Agrupado por clase**: `tag` = `cancel-<bloque>-<fecha>`. Si cancelan 3 personas de la misma clase, el teléfono
  muestra un solo aviso actualizado (`3 cancelaciones · Funcional 19:00 · quedan 5 cupos`), no tres. El texto
  cuenta las cancelaciones de esa ocurrencia en las últimas 2 h.
- Lleva el nombre del socio (es para el equipo, igual que el roster del panel).

Fuera de alcance v1: otros avisos al equipo (nuevo socio, socio vencido, cierre declarado por super-admin), mensajes
libres del gimnasio a todos sus socios, app nativa en tiendas (Capacitor + FCM/APNs), preferencias por tipo de aviso.

## Decisiones (confirmadas con el usuario el 2026-10-13)
1. **Recordatorio**: 60 min antes de la clase. Si la reserva se hizo con menos de 60 min de anticipación, se manda
   30 min antes. Si se hizo con menos de 30 min, no se manda. Una sola vez por reserva.
2. **Sin preferencias por tipo en v1**: activar los avisos = recibir todos los de tu rol. Desactivar = ninguno (en ese
   teléfono).
3. **Cerrar sesión borra la suscripción de ese teléfono** (teléfono compartido = no le llegan los avisos de la persona
   anterior). Mismo criterio que el logout actual, que ya cierra también la sesión de Google.
4. **Socios + equipo del gimnasio**: los socios reciben los avisos 1–4; `GYM_ADMIN` y `PROFESOR` reciben el 5.
   `SUPER_ADMIN` y `DEMO_ADMIN` no se suscriben (el socio demo de `/preview-member` tampoco).
5. **Por dispositivo**: una persona puede tener varios teléfonos suscritos; se envía a todos.

## Arquitectura

```
Teléfono (app instalada)                        Backend (Railway)                     Servicio de push
──────────────────────────                      ─────────────────────                 ────────────────
[Activar avisos] → SwPush.requestSubscription
   (pide permiso al sistema)                    
   → POST /api/push/subscriptions  ───────→  push_subscription (endpoint, claves)
                                                
                                                Evento (cupo, cierre, cron...)
                                                 → publishEvent(PushRequested)
                                                 → AFTER_COMMIT + @Async("pushExecutor")
                                                 → cifra (RFC 8291) + firma VAPID (RFC 8292)
                                                 → POST endpoint  ─────────────────→  FCM (Android/Chrome)
ngsw-worker.js muestra la notificación  ←────────────────────────────────────────── / Apple (iOS 16.4+)
y al tocarla navega a /member                    404/410 → borrar la suscripción
```

Puntos clave:
- **Nunca se envía dentro de la transacción del negocio.** Hoy los correos de lista de espera salen dentro de
  `@Transactional` (`WaitlistService.notifyEntry`). El push NO repite eso: se publica un evento y un listener
  `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)` + `@Async("pushExecutor")` hace el envío.
  Mismo patrón que ya usa `FixedClassService.onMembershipPaid` con `MembershipPaidEvent`. `fallbackExecution = true`
  cubre los llamados sin transacción (los crons).
- **Executor propio y chico** (`pushExecutor`, core 1 / max 2 / cola 200) en `AsyncConfig`, igual que
  `closureNotificationExecutor`: el envío son llamadas HTTP salientes y no debe competir con las requests ni ocupar el
  pool de 5 conexiones de Hikari (el listener lee las suscripciones en una consulta y suelta la conexión antes de
  hacer los POST).
- **Best-effort**: un push que falla nunca rompe nada ni reintenta en bucle. Se loguea y sigue (mismo criterio que
  `MemberLifecycleEmailService.send`).

## Backend

### 1. Migración `V45__push_notifications.sql`
Cargar la skill `supabase-postgres-best-practices` antes de escribirla (regla del proyecto).

```sql
CREATE TABLE push_subscription (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    endpoint         TEXT NOT NULL UNIQUE,
    p256dh           TEXT NOT NULL,
    auth             TEXT NOT NULL,
    user_agent       VARCHAR(300),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_success_at  TIMESTAMPTZ,
    failure_count    INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_push_subscription_user ON push_subscription(user_id);
-- OBLIGATORIO: sin esto Supabase expone la tabla por su API REST (alerta rls_disabled_in_public del 2026-10-13).
ALTER TABLE push_subscription ENABLE ROW LEVEL SECURITY;

-- Recordatorio enviado (evita mandarlo dos veces si el cron corre de nuevo o hay reinicio).
ALTER TABLE reservation ADD COLUMN reminder_pushed_at TIMESTAMPTZ;
```
- `endpoint` UNIQUE: si el mismo teléfono se vuelve a suscribir (u otro socio inicia sesión en ese teléfono), se
  actualiza la fila (upsert por endpoint) y queda asociada al usuario actual.
- `MigrationRlsTest` ya falla si se olvida el `ENABLE ROW LEVEL SECURITY`: correrlo.
- Revisar con `EXPLAIN` la consulta del recordatorio (paso 5) contra los índices existentes de `reservation`
  (V33/V35); agregar un índice parcial solo si el plan lo pide.

### 2. Modelo y repositorio
- `models/PushSubscription.java` (Lombok, como el resto) y `repositories/PushSubscriptionRepository.java`:
  `findByUserId`, `findByUserIdIn`, `findByEndpoint`, `deleteByEndpoint`, `deleteByEndpointAndUserId`.

### 3. Claves VAPID y configuración
- `application.yml`:
  ```yaml
  push:
    vapid:
      public-key: ${PUSH_VAPID_PUBLIC_KEY:}
      private-key: ${PUSH_VAPID_PRIVATE_KEY:}
      subject: ${PUSH_VAPID_SUBJECT:mailto:contacto@mygym.cl}
  ```
- Sin claves configuradas, `PushSender` hace `log.warn` y no envía (mismo patrón que `RESEND_API_KEY` vacía). Así el
  backend local y los tests no necesitan claves.
- Generar el par una sola vez con Node, sin dependencias (P-256, formato base64url que espera el navegador):
  ```js
  const { generateKeyPairSync } = require('crypto');
  const { privateKey, publicKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const pub = publicKey.export({ format: 'jwk' }), priv = privateKey.export({ format: 'jwk' });
  const b = (s) => Buffer.from(s, 'base64url');
  console.log('PUBLIC ', Buffer.concat([Buffer.from([4]), b(pub.x), b(pub.y)]).toString('base64url'));
  console.log('PRIVATE', priv.d);
  ```
- Cargar `PUSH_VAPID_PUBLIC_KEY`, `PUSH_VAPID_PRIVATE_KEY` y `PUSH_VAPID_SUBJECT` como variables en Railway. **La
  privada nunca va al repo ni a un log.** Si cambia el par, todas las suscripciones existentes dejan de servir.

### 4. Envío (`PushSender`)
**Recomendación: implementarlo con el JDK + Nimbus JOSE (ya está en el classpath por
`spring-boot-starter-oauth2-resource-server`), sin librería nueva.** Son ~150 líneas:
- Cifrado del contenido `aes128gcm` (RFC 8291): ECDH P-256 con `KeyAgreement`, HKDF-SHA256 con `Mac`, AES-128-GCM con
  `Cipher`. Validar con los vectores de prueba del Apéndice A del RFC 8291 en un test unitario.
- Cabecera VAPID (RFC 8292): JWT ES256 firmado con Nimbus (`aud` = origen del endpoint, `exp` = ahora + 12 h, `sub` =
  `push.vapid.subject`); `Authorization: vapid t=<jwt>, k=<clave pública>`.
- POST con el `RestClient` que ya se usa para Resend; cabeceras `TTL`, `Urgency` (`high` para cupo y recordatorio,
  `normal` el resto) y `Topic` opcional.
- Respuesta 404/410 → borrar esa suscripción. 429/5xx → `failure_count++` y log, sin reintentar. 201 →
  `last_success_at = now`, `failure_count = 0`.
- Alternativa si se prefiere librería: `nl.martijndwars:web-push`. Antes de agregarla, revisar fecha del último
  release, mantenimiento y CVE de sus dependencias (Bouncy Castle, HTTP client). Es una dependencia más en el build
  de producción; la opción JDK evita ese riesgo.

Contenido (lo que entiende `ngsw-worker.js` sin service worker propio):
```json
{
  "notification": {
    "title": "¡Se liberó un cupo!",
    "body": "Funcional · mié 15 a las 19:00. Tienes 30 min de ventaja.",
    "icon": "/icon-192.png",
    "badge": "/pwa/badge-96.png",
    "tag": "waitlist-123-2026-10-15",
    "renotify": true,
    "data": { "onActionClick": { "default": { "operation": "navigateLastFocusedOrOpen", "url": "/member" } } }
  }
}
```
- `tag` por evento: un segundo aviso del mismo cupo reemplaza al anterior en vez de apilarse.
- Nada sensible en el contenido (sin correo, sin montos). Máximo ~3 KB.
- `badge-96.png`: ícono monocromo (silueta blanca sobre transparente) para la barra de Android; crearlo en
  `app/public/pwa/`. Sin él, Android muestra un cuadrado gris.

### 5. Servicio de avisos y eventos
- `record PushRequested(List<Long> userIds, PushMessage message)` + `PushNotificationService.notifyMembers(...)` que
  solo publica el evento.
- `PushDispatcher` (listener AFTER_COMMIT + `@Async("pushExecutor")`): una consulta `findByUserIdIn`, luego envía.
- `PushMessage` (título, cuerpo, tag, url, ttlSeconds, urgency) armado en un solo lugar (`PushMessages` con un método
  por evento) para que los textos no queden repartidos.

Enganches (al lado del correo, nunca en lugar de él):
| Evento | Dónde | Detalle |
|---|---|---|
| Cupo liberado | `WaitlistService.notifyEntry` | Mismo `headStart` que decide el correo. TTL = minutos de ventaja (cabeza) / hasta la hora de la clase (resto). |
| Cierre | `ClosureNotificationService.sendCancellationEmails` | Un push por socio con todas sus clases canceladas (igual que el correo). TTL 1 día. |
| Por vencer / venció | `MembershipReminderJob.processMember` | Solo al socio (no al admin). TTL 1 día. |
| Clases agotadas | `ReservationService.notifyIfClassesExhausted` | TTL 1 día. |
| Cancelación (al equipo) | `ReservationService.cancel`, después de `waitlistService.onSpotFreed` | Destinatarios: `findByGymIdAndRole(gymId, GYM_ADMIN)` + `PROFESOR`, solo activos. Cupos libres y en espera se calculan en el listener (ya con el commit hecho, así cuentan esta cancelación). Cantidad de cancelaciones de la ocurrencia en las últimas 2 h: `count` sobre `reservation` (`status = CANCELLED`, `updated_at` > ahora − 2 h). TTL = hasta la hora de inicio de la clase. `Urgency: normal`. Si la clase ya empezó, no se manda. |

### 6. Recordatorio de clase (`ClassReminderJob`, nuevo)
- `@Scheduled(fixedDelay = 300_000)` (cada 5 min), zona `America/Santiago`.
- Una consulta: reservas `BOOKED` de **hoy**, sin `reminder_pushed_at`, cuyo bloque empieza entre ahora y ahora +
  60 min, y cuyo socio **tiene al menos una suscripción** (JOIN/EXISTS; así no se marcan como avisadas reservas de
  socios sin push).
- Momento del aviso de cada reserva (`inicio` = fecha + hora de inicio del bloque, zona Chile):
  - `created_at <= inicio − 60 min` → avisar desde `inicio − 60 min` ("empieza en 1 hora").
  - `inicio − 60 min < created_at <= inicio − 30 min` → avisar desde `inicio − 30 min` ("empieza en 30 minutos").
  - `created_at > inicio − 30 min` → nunca; se marca `reminder_pushed_at` igual, para no volver a evaluarla.
  - Se envía si `ahora >= momento` y `ahora < inicio`. Como el cron corre cada 5 min, el aviso puede salir hasta
    5 min después del momento: el texto usa los minutos reales que faltan, redondeados ("empieza en 55 minutos"
    sería raro; redondear a "1 hora" si faltan 50–60 y a "30 minutos" si faltan 25–30).
  - Una clase con fecha de reserva creada para otro día siempre cae en el primer caso.
- Saltar si la clase está cerrada (`GymClosureService` `scheduleFor`/`reasonFor`) o si el socio ya marcó asistencia.
- Marcar `reminder_pushed_at` **en la misma transacción** que arma la lista, y publicar el evento (el envío sale
  después del commit). Si el envío falla, no se reintenta: el recordatorio es prescindible.
- Supuesto: una sola instancia en Railway (igual que los crons existentes). Si algún día hay más de una, este cron
  y los otros necesitan un candado (ShedLock o `SELECT ... FOR UPDATE SKIP LOCKED`).

### 7. Endpoints (`PushController`, bajo `/api/push`)
No van bajo `/api/me/**` (eso exige `MEMBER`) porque también se suscriben admins y profesores. En `SecurityConfig`:
`.requestMatchers("/api/push/**").hasAnyRole("MEMBER", "GYM_ADMIN", "PROFESOR")`, **antes** del `anyRequest()`.
`DEMO_ADMIN` y `SUPER_ADMIN` quedan fuera (403).

| Método | Ruta | Uso |
|---|---|---|
| GET | `/api/push/public-key` | Devuelve la clave pública VAPID (o 204 si no está configurada → el front oculta la función). |
| POST | `/api/push/subscriptions` | Body `{ endpoint, keys: { p256dh, auth } }`. Upsert por `endpoint` al usuario autenticado. Validar: `endpoint` https y de un host de push conocido (`fcm.googleapis.com`, `*.push.apple.com`, `updates.push.services.mozilla.com`, `*.notify.windows.com`), largos máximos, claves base64url. **Este allowlist evita que el backend se use para hacer POST a URLs arbitrarias (SSRF).** |
| DELETE | `/api/push/subscriptions` | Body `{ endpoint }`. Borra solo si pertenece al usuario autenticado. |
| POST | `/api/push/test` | Manda "Avisos activados ✓" a los teléfonos de quien lo pide (socio, admin o profesor). Máximo 3 por hora por usuario (en memoria basta: una instancia). |

## Frontend (`app/`)

### `core/services/push.service.ts` (nuevo)
- `SwPush` de `@angular/service-worker`. `isSupported` = `swPush.isEnabled && 'Notification' in window`.
- En iPhone el push web **solo funciona con la app agregada a la pantalla de inicio** (iOS 16.4+): si es iOS y no
  `PwaInstallService.isStandalone`, estado `needs-install`.
- Estados: `unsupported` · `needs-install` · `default` (no ha decidido) · `denied` · `enabled`.
- `enable()`: pide la clave pública, `swPush.requestSubscription({ serverPublicKey })` (**siempre desde un toque del
  usuario**: iOS lo exige y Chrome castiga los pedidos sin gesto), POST al backend, luego `POST /test`.
- `disable()`: `swPush.unsubscribe()` + DELETE al backend.
- Al iniciar sesión con la app: si el permiso ya está `granted` y hay suscripción, re-POST (por si el navegador la
  rotó). Al cerrar sesión (`AuthService.logout`): `disable()` antes de borrar el token.

### UI en `/member`
Cargar `ui-ux-pro-max` + `emil-design-eng` antes de diseñarla (regla del proyecto). Propuesta de partida:
- Tarjeta chica debajo de `<app-install-banner />` (`member.html:28`), **solo si la app está instalada** y el estado
  es `default`. Nunca pedir permiso al cargar la página: primero se explica, el permiso lo pide el botón.
  ```
  🔔 Activa los avisos en este teléfono
  Te avisamos si se libera un cupo, 1 hora antes de tu clase
  y si el gimnasio cierra.                       [Activar avisos]  [Ahora no]
  ```
- "Ahora no" la oculta 14 días (localStorage, con try/catch).
- Después de activar: toast explícito "Listo, te llegará un aviso de prueba" (regla de confirmación visible).
- `denied`: texto "Los avisos están bloqueados para mygym en este teléfono. Actívalos en Ajustes → Notificaciones."
  sin botón (el sitio no puede volver a pedirlo).
- `needs-install` (iPhone en Safari): "Para recibir avisos en iPhone, primero instala la app" + el mismo popover de
  pasos que ya usa el ícono de instalar.
- En el menú/perfil del socio: interruptor "Avisos en este teléfono" (activar/desactivar) para que no dependa de la
  tarjeta.

### UI en el panel del gimnasio (`/gym-admin`, admin y profesor)
Mismo `PushService`, otro texto y otro lugar. Panel de escritorio con ancho completo (regla del proyecto), pero el
admin también lo abre en el celular, que es donde llegan los avisos:
- Aviso fino arriba del contenido del panel, **solo si la app está instalada** (o en escritorio con Chrome/Edge, que
  también recibe push) y el estado es `default`:
  `🔔 Entérate al instante cuando un socio cancele una clase.  [Activar avisos]  [Ahora no]`
- Mismos estados `denied` / `needs-install` / `enabled` y misma regla: el permiso lo pide el botón, nunca la carga.
- Interruptor "Avisos de cancelaciones en este teléfono" en la pestaña General (admin) o en el encabezado (profesor).
- El tour guiado del demo no se toca (el demo no se suscribe).

### Service worker
Sin cambios en `ngsw-config.json`: `ngsw-worker.js` ya muestra `notification` y ejecuta `onActionClick`. Confirmar en
la prueba real que al tocar el aviso con la app cerrada abre `/member` (no la landing).

## Seguridad y privacidad
- Antes de cualquier deploy: skills de seguridad **y** `supabase-postgres-best-practices` (RLS de la tabla nueva),
  revisando el esquema completo, no solo el diff.
- La suscripción es un dato del dispositivo: se borra al cerrar sesión, al desactivar, al responder 404/410 el
  servicio, y en cascada al borrar al usuario.
- Actualizar `/privacidad`: qué se guarda (dirección del servicio de push del teléfono), para qué, y cómo se borra.
- Logs: nunca el `endpoint` completo ni las claves (solo el id de la suscripción y el host).

## Pruebas
- Unitarias (Mockito, como el resto):
  - `PushSender`: vectores del RFC 8291 (cifrado), cabecera VAPID válida, 410 borra la suscripción, 500 no lanza.
  - `PushController`: rechaza endpoint fuera del allowlist / http / claves inválidas; DELETE de una suscripción ajena
    no borra nada.
  - `ClassReminderJob`: elige solo reservas dentro de los próximos 60 min, salta cerradas/ya avisadas/creadas tarde,
    marca `reminder_pushed_at`.
  - Cada enganche publica el evento con el texto y el `tag` esperados (y el correo se sigue enviando).
  - Cancelación al equipo: la dispara `cancel` (socio) pero NO `cancelAsAdmin`, ni el cierre de emergencia, ni
    `FixedClassService`; llega a admins y profesores activos del gimnasio y a nadie de otro gimnasio; la segunda
    cancelación de la misma clase reutiliza el `tag` y sube el contador.
  - Recordatorio: reserva hecha con 2 h de anticipación → aviso a −60 min; con 45 min → a −30 min; con 20 min → nunca.
  - `MigrationRlsTest` pasa con V45.
- Manual en **el Android real del usuario** (la app ya está instalada ahí): activar → llega el aviso de prueba →
  anotarse a la espera de una clase llena con un socio de prueba y cancelar con otro → llega "se liberó un cupo" →
  tocarlo abre `/member`. Repetir con la app cerrada y con el teléfono bloqueado. Cerrar sesión → ya no llegan.
  Para el aviso al equipo: suscribir el teléfono como admin, cancelar una reserva con un socio de prueba → llega
  "Cancelación · …"; tocarlo abre `/gym-admin`.
- iPhone: probar si hay un equipo disponible; si no, dejarlo anotado como pendiente (igual que el resto de la PWA).

## Orden de ejecución (cada fase se puede desplegar sola)
1. **Infraestructura + aviso de prueba**: V45, `PushSender`, endpoints, `push.service.ts`, tarjeta en `/member`,
   variables en Railway. Se valida de punta a punta con `/test` en el teléfono real.
2. **Lista de espera** (el de más valor).
3. **Cancelación al equipo** (admin y profesores): suma la pantalla de activación en `/gym-admin` y el rol
   `GYM_ADMIN`/`PROFESOR` en `/api/push/**`. Es el aviso que el usuario pidió sumar; va antes del recordatorio porque
   reutiliza lo ya probado y no necesita cron.
4. **Recordatorio de clase** (cron nuevo, con la regla 60/30 min).
5. **Cierre de emergencia + membresía + clases agotadas.**

## Riesgos
- **Android con ahorro de batería agresivo** (algunas marcas) puede retrasar avisos de urgencia normal; los de cupo y
  recordatorio van con `Urgency: high`.
- **El socio no instala la app** → no hay push. El correo sigue llegando; la tarjeta solo aparece con la app instalada.
- **Rotar las claves VAPID invalida todas las suscripciones**: no rotarlas salvo filtración.
- **Cuota de FCM/Apple**: gratis y sin límite práctico para este volumen.
