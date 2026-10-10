# Ajustes del panel de admin (gym-admin + super-admin), 2026-10-10

Plan armado por Opus para que lo ejecute otra sesión. Pedido original del usuario y decisiones ya cerradas abajo. **No re-preguntar lo que ya está decidido.**

## Reglas de la sesión (obligatorias)

- **No desplegar** (ni Railway ni Vercel) hasta que el usuario lo pida explícito en ese turno.
- **No levantar la API local contra Supabase**: aplicaría la migración nueva en la base de producción. Probar con tests Mockito del backend y con un servidor Node falso para el frontend (patrón en la memoria `reference_mygym_e2e_testing_patterns_2026_10_09`).
- Commits **sin** línea `Co-Authored-By`. Hacer commit solo si el usuario lo pide.
- Todo texto de UI en **español chileno** (tú, nunca voseo).
- El árbol de trabajo ya tiene **cambios sin commit de la TV** (`tv-screen.ts`, `tv-screen.scss`, `tv-screen.html`): escala fluida, reloj con segundos, QR más grande, frase en el header. **No tocarlos ni revertirlos.** La TV queda fuera de este trabajo.
- Los archivos `logos/export/ig-post-7..9.*` no son de esta tarea: no agregarlos a ningún commit.

## Contexto verificado en el código

- Panel del gym: `app/src/app/pages/gym-admin/gym-admin.{html,ts,scss}`. Panel super-admin: `app/src/app/pages/admin/gyms/gym-form/gym-form.{html,ts,scss}`. Solo existen en `ng serve landing`.
- Pestañas: `value="branding"` con `<ion-label>Marca</ion-label>` (gym-admin.html ~70, gym-form.html ~34). Horarios = `value="blocks"`.
- Ventana actual: un solo campo `Gym.cancellationWindowHours` (int, horas, default 2 en `GymService` ~110). Se edita en el formulario de identidad (`formControlName="cancellationWindowHours"`, gym-admin.html ~1310, gym-form.html ~1168) vía `PUT /api/gym-admin/identity` y `PUT /api/gyms/{id}/identity` (`GymIdentityUpdateRequest`). Se usa en `ReservationService` (`listOccurrences` → `bookable`, `book` y `cancel` → `requireWithinBookingWindow`), en `PublicGymResponse`/`GymResponse` y en `member.html`/`member.ts` (`canCancel`, hint "Se puede reservar hasta X h antes").
- Lista de espera: `WaitlistService.HEAD_START_MINUTES = 10` (constante global; **no** es 1 hora como creía el usuario). Cron `escalateExpiredHeadStarts` cada 60 s con un `cutoff` global; el email `sendWaitlistHeadStart(..., HEAD_START_MINUTES)` recibe los minutos.
- Marcar pago manual: `GymService.simulatePlanPayment(gymId, memberId, planId)`. Solo setea `planId`/`paidAt` del socio y manda emails. **No guarda ningún registro de pago.** `MarkPaidRequest` solo trae `planId`. Endpoints: `MemberController.markPaid` (gym-admin) y `GymController` ~376 (super-admin). Frontend: `member.service.ts` `markPaid`.
- Lista de bancos: `CHILE_BANKS` en `app/src/app/core/models/gym.model.ts` (ya usada en los datos bancarios del gym; "Otro" abre texto libre).
- "Ver quién va" del socio: `member.html` ~364 (`toggleAttendees`), endpoint `ReservationController` `GET /gym-blocks/{blockId}/occurrences/{classDate}/attendees` y su gemelo en `DemoPreviewController`.
- Profesores: solo `ProfesorController` en `/api/gym-admin/profesores` (GET, POST, DELETE `/{userId}`), restringido al gym del admin. UI en gym-admin.html ~848 (sección "Profesores", `addProfesor`, `removeProfesor`). El super-admin no la tiene.
- Link de alta: tarjeta "Comparte tu link de alta" en gym-admin.html ~207 + `copyJoinLink()` en gym-admin.ts ~2207. El super-admin no la tiene.
- Última migración: `V35__reservation_member_date_index.sql` → la nueva es **V36**.

## Tareas

### 1. Super-admin: link de alta y profesores
- Copiar la tarjeta "Comparte tu link de alta" a la pestaña General de gym-form, armando el link con el gym que se está editando (reusar la misma lógica de `copyJoinLink`, idealmente extraída a un helper compartido).
- Agregar la sección "Profesores" en la pestaña Socios de gym-form (listar, agregar con nombre+email, quitar), mismo diseño que gym-admin.
- Backend: endpoints super-admin `GET/POST /api/gyms/{id}/profesores` y `DELETE /api/gyms/{id}/profesores/{userId}` que reusan el servicio de `ProfesorController` con el `gymId` del path. Mismo esquema de seguridad que el resto de `GymController` (SUPER_ADMIN).

### 2. gym-admin: orden de tarjetas
- En General, mover la tarjeta "Administradores" (~429) **antes** de "Cuenta Pago Online" (~241). En el super-admin no aplica (Pago Online es pestaña aparte).

**De aquí en adelante, cada cambio va en ambos paneles (gym-admin y super-admin).**

### 3. Renombrar pestaña
- "Marca" → "Mi marca" en las dos pestañas. Revisar también textos del tour guiado que nombren la pestaña.

### 4. Dos límites en minutos, al inicio de Horarios
- **Migración V36:** columnas `booking_window_minutes` y `cancellation_window_minutes` (int, not null). Poblar ambas con `cancellation_window_hours * 60` para no cambiar el comportamiento actual. Dejar `cancellation_window_hours` en la tabla sin uso (se borra en una migración posterior, no en esta).
- **Backend:**
  - `Gym`: dos campos nuevos; default para gyms nuevos 120 y 120.
  - `ReservationService`: `listOccurrences` (`bookable`) y `book` usan `bookingWindowMinutes`; `cancel` usa `cancellationWindowMinutes`. Mensajes de error separados: "Las reservas para esta clase cerraron X antes del inicio" / "Solo puedes cancelar hasta X antes del inicio". Formato de X: "2 h", "1 h 30 min", "45 min". `cancelAsAdmin` sigue sin ventana.
  - Exponer ambos en `GymResponse` y `PublicGymResponse`. Quitar `cancellationWindowHours` de `GymIdentityUpdateRequest`.
  - Nuevo endpoint para las reglas de la pestaña Horarios: `PUT /api/gym-admin/booking-rules` y `PUT /api/gyms/{id}/booking-rules`, con un DTO `BookingRulesRequest` que lleva los 4 datos de las tareas 4, 5 y 7. Validación: minutos ≥ 0, máximo 10080 (7 días).
- **Frontend (ambos paneles):** tarjeta "Reglas de reserva" al **inicio** de la pestaña Horarios, con su propio botón Guardar y toast de éxito. Campos en minutos:
  - "Límite para reservar (minutos antes del inicio)". Ayuda: "Hasta cuántos minutos antes de que empiece la clase se puede reservar. Ej.: 30 = para una clase de las 10:00, las reservas se cierran a las 9:30."
  - "Límite para cancelar (minutos antes del inicio)". Ayuda: "Hasta cuántos minutos antes se puede cancelar sin que la clase cuente como usada. Ej.: 120 = se puede cancelar hasta las 8:00."
  - Nota bajo ambos: explicar la diferencia (reservar tarde llena cupos de última hora; cancelar tarde deja cupos que nadie alcanza a tomar).
  - Quitar el campo viejo del formulario de identidad (Mi marca).
- **App del socio (`member.ts`/`member.html`):** `canCancel` usa `cancellationWindowMinutes`; el hint de reserva cerrada usa `bookingWindowMinutes`. Textos: "Se puede reservar hasta X antes del inicio" y "Ya no se puede cancelar (hasta X antes)", con el mismo formato de X.

### 5. Ventaja de la lista de espera configurable
- Migración V36: columna `waitlist_head_start_minutes` int not null **default 30 para todos los gyms** (decisión del usuario; hoy es 10 fijo).
- `WaitlistService`: usar el valor del gym de cada entrada (el cron agrupa por gym o resuelve el gym por bloque) en vez de `HEAD_START_MINUTES`; el email recibe los minutos del gym. Con 0, se avisa a toda la lista al mismo tiempo.
- Campo en la tarjeta "Reglas de reserva": "Ventaja del primero en la lista de espera (minutos)". Leyenda para el admin: "Cuando se libera un cupo en una clase llena, le avisamos primero a quien está primero en la lista de espera y tiene estos minutos de ventaja para reservar. Si no reserva, avisamos a todos los demás. Con 0, avisamos a todos al mismo tiempo."

### 6. Banco al activar un plan
- Migración V36: tabla `manual_payment` (`id`, `gym_id`, `member_id`, `plan_id`, `amount_clp`, `bank`, `registered_by_user_id`, `paid_at`, `created_at`) con FK a gym y a usuario (on delete cascade, alineado con el borrado real de socios/gyms existente) e índice por `(gym_id, paid_at)`.
- `MarkPaidRequest`: agregar `bank` (obligatorio, texto, máx. 100). `simulatePlanPayment` guarda una fila en `manual_payment` (monto = precio del plan al momento del pago, `registered_by` = quien marcó).
- Frontend (modal "Marcar pago" en ambos paneles): selector obligatorio "¿Con qué banco te transfirió?" usando `CHILE_BANKS`; "Otro" abre texto libre (mismo patrón que los datos bancarios del gym). El botón queda deshabilitado sin banco.
- Por ahora **solo guardar** el dato; los informes se arman después. Revisar que la desvinculación de gym (CSV + borrado) contemple la tabla nueva.

### 7. Mostrar u ocultar "Ver quién va" en la app del socio
- Migración V36: columna `show_attendees_to_members` boolean not null default true.
- Interruptor en la tarjeta "Reglas de reserva": "Los socios pueden ver quién va a cada clase". Ayuda: "Si lo apagas, tus socios no verán la lista de asistentes en la app. La pantalla de TV del gimnasio no cambia."
- Backend: `ReservationController` (y `DemoPreviewController`) responden 403 con mensaje claro si está apagado; exponer el flag en `PublicGymResponse`.
- `member.html`: ocultar el botón "Ver quién va" si el flag está apagado.
- **La TV no se toca** (sigue mostrando nombres).

## Verificación esperada

- `mvn test` en `api/` en verde. Tests Mockito nuevos: ventana de reserva y de cancelación separadas (dentro/fuera de cada una), formato del mensaje, ventaja de lista de espera por gym (incluido 0), 403 de asistentes con el flag apagado, `simulatePlanPayment` guarda `manual_payment` con banco, endpoints de profesores del super-admin.
- `npx ng build landing` y `npx tsc --noEmit -p tsconfig.app.json` sin errores.
- Prueba visual con servidor Node falso + `ng serve landing --proxy-config` en ambos paneles y en `/member`: tarjeta de reglas al inicio de Horarios, guardar, modal de pago exige banco, botón "Ver quién va" oculto con el flag apagado, mensajes de ventana en el socio.
- Al terminar, reportar al usuario qué quedó probado y qué no, y **esperar** su orden antes de commit o deploy.
