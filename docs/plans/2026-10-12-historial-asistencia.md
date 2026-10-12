# Historial: asistencia real, marcado a mano, resúmenes y ranking, 2026-10-12

Plan armado para que lo ejecute otra sesión. **No implementar nada fuera de este plan.** La idea general es del usuario; el diseño de abajo es el que recomiendan las skills de UX (ui-ux-pro-max + emil-design-eng) sobre esa idea.

## Pedido original

En la sección **Historial** del panel del gym, un botón que muestre el resumen de los socios que **reservaron** frente a los que **realmente fueron** (marcaron asistencia con el QR). Se abre un modal con el resumen de ese día y, al tocar una clase, se ven los que marcaron asistencia y los que no.

Ampliación del mismo día: el admin (y el profesor) puede **marcar asistencia a mano**, hay **resúmenes semanales y mensuales** y un **ranking de los que más faltan** (secciones 4, 5 y 6).

## Reglas de la sesión (obligatorias)

- **No desplegar** hasta que el usuario lo pida explícitamente en ese turno.
- **No levantar la API local contra Supabase.** El backend se prueba con tests Mockito y el frontend con el servidor Node falso (memoria `reference_mygym_e2e_testing_patterns_2026_10_09`; `/gym-admin` solo existe en `ng serve landing`).
- Commits **sin** `Co-Authored-By`, y solo si el usuario lo pide.
- Textos en **español chileno** (tú, nunca voseo).

## Contexto verificado en el código

- **Historial** está en `app/src/app/pages/gym-admin/gym-admin.html` (~1579, `section() === 'history'`) y `gym-admin.ts`. Muestra la semana día por día (`historyWeekSummary`, ~556). Cada día se expande en tarjetas de clase con un botón "Ver quién reservó" (`toggleHistoryAttendees`).
- Los datos de la semana llegan en **una sola llamada**: `GET /api/gym-admin/history-attendees?from&to`, que llama a `ReservationService.getOccurrenceAttendeesForRange`. Solo devuelve ocurrencias con reservas BOOKED, de bloques **activos**. El super-admin tiene el mismo endpoint en `GymController` (~185) y el mismo Historial en `gym-form`.
- **El dato de asistencia ya existe y no se envía:**
  - Cada reserva tiene `checkedInAt`. Si es null, la persona no marcó asistencia.
  - `MemberReservation` (el record interno) ya lo trae.
  - Pero el DTO `AttendeeResponse(id, name, email, photoUrl, reservationId)` lo descarta.
- "**No vino**" ya está definido en el proyecto (comentario en `Reservation.checkedInAt`): reserva BOOKED, la clase **ya terminó** y `checkedInAt` es null. No es un estado guardado; se calcula en el momento.
- Las reservas canceladas no cuentan, porque el endpoint solo trae BOOKED. Las reservas de clases fijas cuentan igual que las normales.
- El celular del socio (`Member.phone`) y `whatsappUrl()` (`core/utils/member-profile.ts`) ya existen.

## Diseño (recomendado por las skills de UX)

Principios aplicados:
- **Resumen antes que detalle:** el número del día se ve sin abrir nada.
- **El estado no depende solo del color:** ícono y texto además del verde o rojo.
- **Números alineados** (tabular-nums).
- **Sin animación** en lo que se toca muchas veces (cambiar de pestaña).
- **Modal** de 200–250 ms con ease-out.
- **No marcar como falta lo que todavía no termina.**

### 1. En la lista de días (sin abrir nada)

En el encabezado de cada día pasado, junto al texto actual ("3 clases · 24 personas"), va un chip de asistencia:

> `✓ 18 de 24 vinieron · 75%`

- Color según la tasa, siempre con el número visible: verde ≥ 80%, amarillo 50–79%, rojo < 50%.
- **Hoy:** cuenta solo las clases que ya terminaron, por ejemplo "✓ 6 de 8 (clases terminadas)".
- **Días futuros:** sin chip.

### 2. Botón "Asistencia" en cada día con reservas pasadas

Abre el **modal "Asistencia del martes 13 de octubre"**. Si el día tiene una sola clase, se abre directo en esa clase.

**Arriba, tres cifras grandes:**

| Reservaron | Vinieron | No vinieron |
|---|---|---|
| 24 | 18 | 6 |

Debajo, una barra de proporción (vinieron / no vinieron) con el porcentaje en texto: "75% de asistencia".

**Lista de clases del día**, ordenada por hora. Una fila por clase:

`19:00 – 20:00 · GAP · Camila (profe)` — barra mini — `7 / 10` — chevron

- Las filas de clases que **no han terminado** dicen "En curso" o "Por empezar" en gris, sin barra roja ni "no vinieron".
- Al tocar una fila se abre el detalle **dentro del mismo modal** (vista con "← Volver"), no otro modal encima.

### 3. Detalle de una clase

- **Encabezado:** clase, hora y "7 de 10 vinieron".
- **Dos pestañas segmentadas:** "Vinieron (7)" y "No vinieron (3)". Arranca en **No vinieron**, que es lo accionable; si nadie faltó, arranca en Vinieron.
- **Vinieron:** foto o inicial, nombre y la hora en que marcó ("19:04"), con ✓ verde.
- **No vinieron:** foto o inicial, nombre y ✕. Si el socio tiene celular, un botón de WhatsApp que abre la conversación (mismo patrón `whatsappUrl` de la lista de Socios). Sirve para preguntarle qué pasó o recordarle cancelar a tiempo.
- **Clase que no ha terminado:** en vez de "No vinieron" se muestra "Sin marcar todavía" y no se acusa a nadie de faltar.

### 4. Marcar asistencia a mano (pedido del usuario, 2026-10-12)

Para quien vino y no escaneó el QR (sin celular, sin batería, se le olvidó), o para corregir un error.

- **Dónde:** en el detalle de una clase del modal de Asistencia. Cada socio tiene un botón de estado que alterna entre **"Vino"** y **"No vino"**. En una clase en curso, el botón de los que aún no marcan dice "Marcar que vino".
- **Quién:** GYM_ADMIN y **PROFESOR** (es quien está en la clase), más el super-admin. El DEMO_ADMIN no, como el resto de las escrituras.
- **Cuándo:** desde 10 minutos antes del inicio (la misma ventana del QR, `CHECKIN_EARLY_WINDOW`) hasta **7 días después** de la clase. Nunca en una clase futura. Pasados los 7 días queda de solo lectura, para que el historial no se reescriba meses después.
- **Desmarcar:** se puede, también un check-in hecho por QR, para corregir errores. Siempre queda quién lo hizo (ver datos).
- **Distinguir el origen:** en la lista, un check-in por QR muestra la hora ("19:04") y uno manual dice "19:04 · a mano". Así el dueño sabe cuántos marcan con el QR de verdad.
- **Efectos:** el cupo del plan **no cambia**, porque ya se descontó al reservar. Memoria Viva (`WorkoutService.getPendingWorkout`) toma la clase como asistida, igual que con el QR. Es lo correcto.
- **Feedback:** el botón cambia al instante (actualización optimista) y, si el servidor falla, vuelve atrás con un toast del motivo (`errorText`).
- **Fuera de alcance:** sumar a alguien que vino **sin reserva** (walk-in). Eso crearía una reserva y descontaría cupo; es otra decisión.

### 5. Resumen semanal y mensual

Arriba del Historial, un selector segmentado **"Semana | Mes"**. La semana es la que ya se navega con las flechas y el mes es el calendario de la fecha elegida. Debajo, una tarjeta de resumen del período:

- **Cuatro cifras:**
  - Reservaron.
  - Vinieron.
  - No vinieron.
  - **Tasa de asistencia**, con su variación frente al período anterior: "▲ 4 pts vs. semana pasada" o "▼ 2 pts vs. septiembre". La flecha va con su texto; no se depende del color.
- **Por clase:** tabla con cada clase (nombre + día + hora), sus reservas, asistencias y tasa, ordenada **de peor a mejor tasa**. Sirve para ver qué horario falla. Una mini barra por fila.
- **Por día de la semana:** 7 barras con la tasa de cada día (lunes a domingo) para ver si los viernes se cae la asistencia. Barras simples con CSS (`scaleY`), sin librería de gráficos. Cada barra lleva su número.
- Solo cuenta clases **ya terminadas**; las de hoy en curso no entran en la tasa.
- **Del número a las personas (pedido del usuario):** todo número del resumen se puede tocar y muestra los socios que hay detrás, en una vista dentro del mismo resumen con "← Volver" (sin abrir otro modal encima):
  - **Una fila de la tabla por clase** (por ejemplo "GAP · lunes 19:00 · octubre") lista a los socios que reservaron esa clase en el período, con cuántas veces vino cada uno: "Camila Rojas — 3 de 4 (75%)". Va ordenada de más faltas a menos, con WhatsApp para los que faltaron al menos una vez.
  - **Una barra por día de la semana** (por ejemplo "Viernes") muestra la misma lista, pero de todas las clases de ese día de la semana en el período.
  - **La cifra "No vinieron"** de arriba abre la lista completa de socios con al menos una falta en el período (el ranking muestra solo los 10 primeros; esta los muestra todos, paginada de a 25).
  - **Las cifras "Reservaron" y "Vinieron"** abren la lista de todos los socios del período con su asistencia.
  - En cada lista, tocar un socio abre su detalle del período: cada clase reservada con fecha, hora y si vino (✓ QR / ✓ a mano / ✕). Desde ahí, si la clase está dentro de los 7 días, se puede corregir la asistencia (mismo botón de la sección 4).
  - **Filas y barras tocables:** se ven como tocables (chevron, cursor y foco visible) y miden 44 px o más.
- El listado diario de abajo (lo que ya existe) sigue igual en la vista Semana. En la vista Mes, el listado diario no se muestra: solo el resumen y el ranking.

### 6. Ranking de los que más faltan

En la misma vista de resumen (semana o mes), una tarjeta **"Los que más faltan"**:

- **Top 10** socios del período ordenados por cantidad de faltas. Si empatan, gana la peor tasa.
- Solo socios con **al menos 3 reservas** en el período, para no exhibir a alguien que reservó una vez y faltó. El mínimo se explica en una línea bajo el título.
- **Por fila:** foto o inicial, nombre, "4 faltas de 9 reservas (56%)", fecha de la última falta y botón de **WhatsApp** si tiene celular.
- Tocar el nombre lleva a Socios con el buscador precargado con ese socio (el buscador ya acepta texto).
- **Sin datos:** "Nadie faltó en este período" (o "Todavía no hay clases terminadas").
- **Privacidad:** lo ven GYM_ADMIN, PROFESOR y super-admin; **nunca un socio**. No se manda nada automático a los socios del ranking.

## Cambios técnicos (fases 4-6)

> **Orden de ejecución:** primero las fases 1-3 (sección "Cambios técnicos (fases 1-3)", más abajo), después estas. La fase 4 reusa el modal de la fase 1.

### Datos (V39)

```sql
ALTER TABLE reservation ADD COLUMN checkin_method VARCHAR(10);   -- 'QR' | 'MANUAL'; null = sin marcar
ALTER TABLE reservation ADD COLUMN checked_in_by_user_id BIGINT;  -- quién marcó/desmarcó a mano (null en QR)
UPDATE reservation SET checkin_method = 'QR' WHERE checked_in_at IS NOT NULL;
```

- `ReservationService.checkIn` (el del QR) guarda `checkinMethod = QR`.
- **Índice para los resúmenes:** las consultas filtran por los bloques del gym y un rango de fechas, y agrupan. El índice parcial existente `uk_reservation_slot_booked (gym_block_id, member_id, class_date) WHERE status='BOOKED'` sirve si el planner lo usa. Antes de agregar uno nuevo hay que medir: correr `EXPLAIN ANALYZE` de las dos consultas de agregación contra una copia o con `railway run` en solo lectura. Si sale `Seq Scan`, agregar `(gym_block_id, class_date) WHERE status = 'BOOKED'`. No agregar índices "por si acaso".

### Endpoints

| Método | gym-admin / profesor | super-admin | Qué hace |
|---|---|---|---|
| PUT | `/api/gym-admin/reservations/{id}/attendance` body `{ attended: boolean }` | `/api/gyms/{gymId}/reservations/{id}/attendance` | marca o desmarca a mano |
| GET | `/api/gym-admin/attendance-summary?from&to` | `/api/gyms/{gymId}/attendance-summary?from&to` | resumen + por clase + por día + ranking |
| GET | `/api/gym-admin/attendance-summary/members?from&to[&blockId][&weekday][&onlyMissed][&page][&size]` | `/api/gyms/{gymId}/attendance-summary/members?...` | socios detrás de un número: filtrable por clase, por día de la semana o solo con faltas; paginado (25, tope 50) |
| GET | `/api/gym-admin/attendance-summary/members/{memberId}?from&to` | `/api/gyms/{gymId}/attendance-summary/members/{memberId}?...` | detalle de un socio en el período: cada reserva con fecha, clase, si vino y cómo (QR/manual) |

- **SecurityConfig:** `/api/gym-admin/reservations/*/attendance` va con `GYM_ADMIN` y `PROFESOR`, antes del matcher genérico (mismo patrón que clases fijas). El GET de resumen ya queda abierto a los dos por el matcher de GET.
- **Marcar a mano:**
  - Validar que la reserva sea BOOKED y de un bloque de **ese** gym; si no, 404 sin revelar que existe en otro.
  - Validar la ventana: desde inicio − 10 min hasta 7 días después del fin. Fuera de ella, 400 con un mensaje claro: "Solo puedes marcar asistencia desde que empieza la clase y hasta 7 días después."
  - Marcar: `checkedInAt = now`, `checkinMethod = MANUAL`, `checkedInByUserId = actor`.
  - Desmarcar: `checkedInAt = null`, `checkinMethod = null`, `checkedInByUserId = actor`. Así queda quién lo quitó.
  - Es idempotente: marcar dos veces no cambia la hora original.
- **Resumen:** `AttendanceSummaryService`, con dos consultas agregadas (JPQL o nativas con parámetros, nunca concatenando) sobre las reservas BOOKED de los bloques del gym en el rango:
  1. **Por ocurrencia** (bloque + fecha): `count(*)`, `count(checked_in_at)`. En Java se descartan las ocurrencias que no han terminado y se arma: total, por clase y por día de la semana.
  2. **Por socio:** `count(*)` y `count(checked_in_at)` de ocurrencias **terminadas**, más la fecha de la última falta. En Java se aplica el mínimo de 3 reservas y se ordena para el top 10, y se cruza con `AppUser` (nombre, foto, celular) en **una** consulta `findAllById`.
  3. El período anterior (mismo largo, justo antes) se calcula con la consulta 1 sobre ese rango; basta el total para la variación.
  4. **Lista de socios** (`/members`): la misma consulta 2, con filtros opcionales `blockId` (validado: debe ser de ese gym, si no 404) y `weekday`, aplicados en el WHERE (bloques del gym de ese día). También `onlyMissed`. Se pagina en la base (ORDER BY faltas desc, tasa asc, nombre; LIMIT/OFFSET) y luego un `findAllById` de los socios de esa página. Nunca N+1.
  5. **Detalle de un socio** (`/members/{memberId}`): valida que el socio sea MEMBER de ese gym (404 si no). Trae sus reservas BOOKED del rango con su bloque en una consulta (rango ≤ 62 días, así que está acotado).
  - Rango máximo permitido: 62 días (un mes con margen). Si piden más, 400.
  - El "terminó" se calcula con la hora de fin del bloque en `America/Santiago`, el mismo criterio de `isPastOccurrence`.
- **DTO:** `AttendanceSummaryResponse(totals, previousTotals, byClass[], byWeekday[], topAbsentees[])`. Cada absentee lleva `memberId, name, photoUrl, phone, booked, attended, missed, lastMissedDate`.
  - `byClass[]` lleva `blockId` y `byWeekday[]` lleva `weekday`, para poder abrir la lista correspondiente.
  - Lista de socios: `PageResponse<MemberAttendanceRow>` (mismos campos que un absentee).
  - Detalle: `MemberAttendanceDetail(member, rows[{reservationId, classDate, blockLabel, startTime, ended, attended, checkinMethod, checkedInAt, editable}])`. `editable` = dentro de la ventana de marcado manual.

### Frontend (fases 4-6)

- **Modal de Asistencia (fase 1):** cada fila del detalle lleva el botón de estado. Emite `toggleAttendance(reservationId, attended)` y el padre llama a la API y actualiza `attendeesByKey`, para que el chip del día se recalcule solo.
- `Attendee` agrega `checkinMethod: 'QR' | 'MANUAL' | null`.
- **Componente nuevo `attendance-summary`** (`pages/admin/gyms/attendance-summary/`), compartido por gym-admin y gym-form. Recibe el `AttendanceSummary`, el período y su etiqueta, y renderiza: las cifras, la tabla por clase, las barras por día y el ranking.
  - **Navegación interna** con un signal `view`: `'summary' | { list: filtro } | { member: id }`. Las vistas lista y detalle piden sus datos al padre por `@Output` (el componente no llama a la API), y muestran "← Volver" a la vista anterior.
  - Al corregir una asistencia desde el detalle de un socio, se refresca el resumen del período (cifras, tabla y ranking) para que nada quede desfasado.
- **Historial:** agrega el selector "Semana | Mes" y el resumen, que se pide al cambiar de período (uno por navegación, cacheado por rango igual que `historyLoadedRanges`).
- **Tour de la demo:** paso nuevo apuntando al resumen ("Mira qué clases y qué socios fallan más").
- Mismo estándar visual: `tabular-nums`, barras con `transform`, íconos con texto, filas de 44 px o más y `prefers-reduced-motion`.

### Tests

- **Mockito, `ManualAttendanceTest`:**
  - Marca y desmarca.
  - Es idempotente.
  - Rechaza fuera de la ventana: futura y a más de 7 días.
  - Rechaza una reserva de otro gym (404).
  - El PROFESOR puede.
  - Marcar guarda `MANUAL` y quién.
  - El QR guarda `QR`.
- **Mockito, `AttendanceSummaryTest`:**
  - No cuenta clases sin terminar.
  - Ranking con el mínimo de 3 reservas.
  - Desempate por tasa.
  - Variación contra el período anterior.
  - Rango mayor a 62 días da 400.
  - `/members` con `blockId` de otro gym da 404.
  - Filtros `weekday` y `onlyMissed` aplicados.
  - Paginación y orden (faltas desc, tasa asc).
  - `/members/{id}` de otro gym da 404.
  - `editable` respeta la ventana de 7 días.
- **Vitest:** las funciones puras nuevas del resumen (etiqueta de variación y orden por tasa).

### Casos borde (fases 4-6)

| Caso | Esperado |
|---|---|
| Marcar a mano una clase de mañana | 400, botón deshabilitado en la UI |
| Marcar una clase de hace 10 días | 400, la UI la muestra de solo lectura |
| Desmarcar un check-in hecho por QR | Permitido; queda `checkedInByUserId` del que lo quitó |
| Socio con 2 reservas en el mes y 2 faltas | No entra al ranking (mínimo 3) |
| Mes sin clases terminadas | Resumen en cero con texto explicativo, sin tasa (no "0%") |
| Semana actual a medio camino | La tasa solo cuenta lo terminado; el texto lo dice ("hasta hoy") |
| Tocar una clase con 0 reservas en el período | No es tocable (la fila no se muestra en la tabla) |
| Corregir asistencia desde el detalle de un socio | La vista se actualiza y, al volver, el resumen y el ranking ya reflejan el cambio |
| Lista de "No vinieron" con 60 socios | Paginada de a 25 con "Ver más" |

## Cambios técnicos (fases 1-3)

### Backend (mínimo)

1. `AttendeeResponse` agrega `Instant checkedInAt` (el último campo).
   - Este DTO **solo lo ven admins** (gym-admin, profesor y super-admin), así que no expone nada nuevo a otros socios.
   - Actualizar los dos `toBatchResponse` (`GymAdminController` y `GymController`) y cualquier otro `new AttendeeResponse(` (buscarlos con grep: también los usan el roster de Horarios y la búsqueda de reservas).
2. **Sin endpoint nuevo:** el Historial ya trae la semana completa en una llamada, y con `checkedInAt` el frontend calcula todo.
3. **Celular para WhatsApp:** dentro del detalle, buscar el celular en una sola llamada al abrir el modal, o agregar `phone` a `AttendeeResponse`. Recomendado: **agregar `phone` a `AttendeeResponse`**. El profesor ya ve el celular en Socios y no suma consultas.
4. **Test Mockito:** que el batch de historial devuelva `checkedInAt` y `phone` de cada reserva.

### Frontend

1. `core/models/member.model.ts`: `Attendee` agrega `checkedInAt: string | null` y `phone: string | null`.
2. **Función pura** `core/utils/attendance.ts`, con su spec (vitest), para no repetir la regla en tres lugares:
   - `occurrenceEnded(classDate, endTime, now)`: compara en zona `America/Santiago` (mismo criterio que `isPastOccurrence` del backend).
   - `attendanceOf(attendees, ended)` → `{ booked, attended, missed, pending }`. Si la clase no terminó, los sin check-in son `pending`, no `missed`.
   - `rateTone(rate)` → `'good' | 'warn' | 'bad'`.
3. **Chip del día** en `historyWeekSummary`: sumar por día `booked/attended/missed`, solo de clases terminadas.
4. **Componente nuevo `attendance-modal`** (`pages/admin/gyms/attendance-modal/`), compartido por gym-admin y gym-form con el mismo patrón que `ClosureModal` (solo recibe datos y emite; no llama a la API):
   - Entradas: fecha, bloques del día, asistentes por bloque y la hora actual (para poder testearlo).
   - Dos vistas internas (resumen y detalle de clase), con un signal `selectedBlockId`.
5. **Integración:**
   - Botón "Asistencia" en el encabezado de cada día de Historial (gym-admin y gym-form).
   - `data-tour="history-attendance"` en el primero, más un paso nuevo en el tour de la demo: "Reservaron vs. vinieron".
6. **Estilos:**
   - Barras con `transform: scaleX()` (no animar `width`).
   - Cifras con `tabular-nums`.
   - Filas de 44 px o más.
   - Íconos ✓ y ✕ con `aria-hidden` y el texto al lado.
   - `prefers-reduced-motion`.

### Casos borde (tests del util y prueba visual)

| Caso | Esperado |
|---|---|
| Clase de hoy que todavía no termina | Cuenta como "pendiente", nunca como "no vino" |
| Día sin reservas | Sin chip ni botón |
| Todos vinieron | Detalle abre en "Vinieron", "No vinieron (0)" con texto "Nadie faltó 🎉" (sin emoji si el resto del panel no usa emojis) |
| Socio con check-in en una clase y reserva en otra | Se cuenta por reserva, no por socio |
| Clase de un bloque hoy inactivo | No aparece (el endpoint ya filtra bloques activos); anotarlo como límite conocido |
| Socio sin celular | Sin botón de WhatsApp en "No vinieron" |

## Verificación

1. `mvn test` de las clases tocadas; `npx tsc --noEmit -p tsconfig.app.json`; `npx ng build landing`; `npx vitest run src/app/core/utils --globals`.
2. Prueba visual con el servidor Node falso: semana con días pasados, hoy y futuros; una clase con todos presentes, otra con faltas y otra en curso. Revisar a 430 px y 1280 px.
3. Correr las skills de seguridad y performance sobre el diff (memoria `feedback_audit_skill_groups`).
