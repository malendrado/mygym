# "En clase": quién viene y su Memoria Viva, 2026-10-12

Plan para que lo ejecute otra sesión. **No implementar hasta que el usuario lo apruebe.**

## Decisiones del usuario (2026-10-12)

1. **Se muestran TODAS las clases en curso** (una píldora por cada una). Un mismo profesor puede dar más de una clase a la vez, así que no se filtra por profesor ni se vincula el bloque a una cuenta.
2. **No es una pestaña nueva:** va **dentro de "Socios"**, que se divide en dos subsecciones: **"Mis socios"** (todo lo que hay hoy) y **"En clase"**.
3. **El profesor puede anotar la rutina por el alumno** (sí).
4. **Sin aviso fuera del panel** (ni email ni push): solo el banner dentro del panel.
5. **Último registro en la tarjeta y los últimos 5 en el detalle** (sí).
6. **Admin del gym:** igual que el punto 2 (dentro de Socios, mismas dos subsecciones). Lo mismo para el super-admin en la ficha del gym.

## El problema

Hoy la rutina de un socio está escondida: Socios → buscar al socio → "Acciones" → "Rutina". Un profesor parado frente a 12 personas no puede hacer eso persona por persona, y por eso Memoria Viva casi no se usa en el momento en que más sirve: justo antes y durante la clase.

**Lo que el profesor necesita:** en un toque, ver la clase en curso (o la que viene) y, para cada alumno, *qué hizo la última vez* y *qué le toca hoy*.

## Lo que ya existe (verificado en el código)

- **Clase en curso y siguiente:** `TvScreenService` ya calcula qué bloques están en curso ahora y cuál es el siguiente (respetando cierres), con su lista de asistentes en una sola consulta. Se reutiliza esa lógica; no se reinventa.
- **Memoria Viva:**
  - Plan activo por socio con N días cíclicos.
  - Registros por reserva con check-in.
  - "Qué le toca" ya se calcula (`WorkoutService.getNextPlanDay`).
  - Historial de los últimos 30 registros (agregado hoy).
- **Asistencia:**
  - Check-in por QR.
  - Marcado a mano (agregado hoy, ventana desde 10 min antes de la clase).
  - El modal de asistencia del día.
- **Lo que NO hay:**
  - Notificaciones push: no hay infraestructura web-push.
  - Vínculo entre el profesor y sus clases: `GymBlock.instructorName` es texto libre, no un usuario.

## Dónde va: subsección "En clase" dentro de Socios (decisión del usuario)

Se descartaron las otras opciones:

| Opción | Por qué no |
|---|---|
| Dentro de Horarios ("Ver quién reservó") | Horarios es para *configurar* la semana; mezclarlo con la clase de ahora lo recarga y obliga a buscar el bloque. |
| Dentro de Socios | Es una lista de todo el gym; el profesor necesita *los de esta clase*, no buscar. |
| En la TV | La TV es pública: las rutinas y notas de un alumno son datos personales. Nunca en pantalla. |
| Solo un aviso (push) | No hay infraestructura y un aviso solo sirve si lleva a una pantalla útil. Primero la pantalla. |

**Decisión (del usuario):** la pestaña **Socios** pasa a tener dos subsecciones, con un selector segmentado arriba:

> **Mis socios** | **En clase**

- **Mis socios:** todo lo que hay hoy en Socios, sin cambios (calugas, buscador, lista, acciones, profesores, agregar socio).
- **En clase:** el flujo de abajo.
- **Para quién:** igual para profesor, admin del gym y super-admin (en la ficha del gym).
- **Al entrar a Socios:**
  - Si hay una clase en curso o una que empieza en 15 minutos o menos, se abre en **"En clase"**.
  - Si no, en **"Mis socios"**.
  - El selector siempre permite cambiar.
  - Confirmado por el usuario.
- **Tocar una caluga de estado** (Activos, Vencidos, etc.) lleva siempre a "Mis socios".

## El flujo

### 1. Elegir la clase (cero toques en el caso normal)
- **Arriba, un selector de píldoras:** "**En curso** · 19:00 GAP · Camila" | "**Siguiente** · 20:00 CrossFit". La píldora de la clase en curso parpadea suave (respetando `prefers-reduced-motion`).
- **Selección automática:**
  - Si hay una clase en curso, queda elegida.
  - Si no, queda elegida la siguiente.
  - Si hay dos clases a la misma hora, aparecen dos píldoras.
- **Todas las clases en curso:** se muestran todas, sin filtrar por profesor (decisión del usuario: un profesor puede dar varias a la vez).
- **Sin clases hoy:** "No hay más clases hoy. La próxima es mañana 07:00 · GAP" y se muestra esa.
- **Recarga sola** cada 60 s y al volver a la pestaña. Así el check-in por QR aparece sin recargar, y la clase cambia de "siguiente" a "en curso" sola.

### 2. Encabezado de la clase
"19:00–20:00 · GAP · 8 de 10 reservaron · ✓ 5 llegaron". Debajo, avisos accionables (chips que filtran la lista):
- "**3 sin rutina**". Son los que no tienen plan; tocarlo filtra a ellos.
- "**2 no anotaron la última**". Hicieron check-in la clase anterior pero no registraron.
- "**1 primera clase**". Es su primera reserva en el gym (bienvenida).

### 3. La lista: una tarjeta por alumno (la "memoria" a la vista)
Cada tarjeta, sin abrir nada:
- Foto o inicial y nombre.
- Estado de llegada: ✓ llegó 18:57 (QR / a mano) o "no ha llegado".
- **"Hoy le toca: Día 3 · Piernas"**, lo más grande, porque es lo que el profe necesita decir.
- **"La última vez (lun 7 oct): Día 2 · Espalda"** + los 2–3 primeros ejercicios con su nota (por ejemplo "Sentadilla — 4x8 60 kg"), en una línea truncada.
- Si se desvió: "Le tocaba Día 2 pero hizo cardio", en amarillo con ícono.
- Si no tiene rutina: "Sin rutina", con botón **"Crear rutina"**.

**Orden:** primero los que llegaron, luego los que no, cada grupo por nombre. Los "sin rutina" no se esconden: son lo accionable.

### 4. Tocar una tarjeta: hoja con el detalle (no otro modal encima)
Hoja inferior en mobile y panel lateral en desktop, con:
- **Qué le toca hoy** y el plan completo (los N días, con el de hoy marcado).
- **Sus últimos 5 registros** (fecha, día, ejercicios y notas) y un enlace a "ver los 30".
- **Acciones rápidas:**
  - **Marcar que llegó**, si no hizo QR. Reusa el marcado a mano de hoy.
  - **Editar rutina** (el modal existente).
  - **WhatsApp**, si tiene celular.
- **"Anotar por él" (aprobado):** el profesor registra lo que hizo el alumno en esa clase, con el mismo formulario del socio (día del plan sugerido o texto libre, ejercicios y notas).
  - Solo sobre una reserva con check-in. Si el alumno no hizo QR, primero se marca "llegó".
  - Queda guardado quién lo anotó, y se muestra "anotado por Camila (profe)".
  - Si el socio ya lo había anotado, el profesor puede corregirlo.

### 5. Recordatorio
- **Dentro del panel, siempre (sin infraestructura nueva):** 15 minutos antes de una clase, un banner fijo arriba en cualquier pestaña: "Tu clase de 19:00 · GAP empieza en 12 min — 8 alumnos, 3 sin rutina → **Ver**". Lleva a "En clase".
- **Fuera del panel: DESCARTADO por el usuario (no se hace).** Referencia de lo que se evaluó: un aviso que le llega al profesor **aunque no tenga mygym abierto**, porque el banner de arriba solo se ve si está mirando el panel. Dos formas:
  - **Email** 30 min antes: "Tu clase de las 19:00 · GAP: 8 alumnos, 3 sin rutina → Ver la clase". Usa lo que ya existe (Resend). Como no se sabe qué profesor da cada clase, iría a todos los profesores y al admin. Con varias clases al día, son varios emails.
  - **Notificación en el celular (push),** como la de WhatsApp: requiere que el profesor tenga la app instalada y acepte permisos, más infraestructura nueva (web-push, VAPID, y un cron que la dispare). En iPhone solo funciona con la app instalada.
  - **Decisión:** no se hace ninguno.

### 6. Cerrar el ciclo con el socio (para que haya memoria que mostrar)
La pantalla del profesor solo sirve si los socios anotan. Dos mejoras chicas del lado del socio:
- En su app, en la reserva de hoy: "**Hoy te toca: Día 3 · Piernas**" (el mismo dato). El socio llega sabiendo.
- Al hacer check-in por QR, el toast de éxito lleva un botón "**Anotar lo que hice**" que abre su registro. Hoy tiene que ir a buscar la pestaña.

## Cambios técnicos

### Backend
- **Endpoint nuevo:** `GET /api/gym-admin/class-board?date&blockId` (gym-admin, profesor y demo en lectura) y su contraparte `GET /api/gyms/{gymId}/class-board?...` (super-admin).
  - Sin parámetros, devuelve las clases "en curso" y "siguiente", reusando el cálculo de `TvScreenService` extraído a un método compartido.
  - Con `blockId` + `date`, devuelve esa ocurrencia (para cuando el profe elige otra píldora).
- **Por cada asistente:** `memberId, name, photoUrl, phone, reservationId, checkedInAt, checkinMethod, hasPlan, planName, nextDay {id, title}, lastLog {classDate, dayTitle, deviated, doneLabel, exercises[0..3]}, unloggedLast, firstClass`.
- **Sin N+1:** la clase tiene como máximo su cupo (≈10–30 alumnos). Se resuelve con un número fijo de consultas por llamada:
  - Reservas de la ocurrencia.
  - `findAllById` de socios.
  - Planes activos `findByMemberIdInAndActiveTrue`.
  - Días de esos planes `findByWorkoutPlanIdIn`.
  - Reservas con check-in de esos socios.
  - Registros `findByReservationIdIn`.
  - "Primera clase": un `count` agrupado por socio.

  `getNextPlanDay` se reusa en memoria por socio.
- **Detalle de un alumno:** reusa `GET .../members/{id}/workout/plan` y `/logs` (ya existen).
- **"Anotar por él" (aprobado):**
  - `POST /api/gym-admin/members/{id}/workout/logs/{reservationId}` y `PUT .../logs/{logId}`, con la misma validación que el socio (reserva con check-in, una por reserva, el día pertenece al plan).
  - Contrapartes super-admin en `/api/gyms/{gymId}/...`.
  - **V40:** `member_workout_log.logged_by_user_id BIGINT` (null = lo anotó el socio).
  - **SecurityConfig:** ya cubre `/api/gym-admin/members/*/workout/**` para GYM_ADMIN y PROFESOR.
- **Seguridad:**
  - Todo filtrado por el gym del JWT.
  - La demo solo lee.
  - El profesor no ve nada fuera de su gym.
  - Las rutinas nunca van a la TV.

### Frontend
- **Componente `class-board`** (`pages/admin/gyms/class-board/`), compartido por gym-admin y gym-form, igual que `attendance-summary`.
- **Subsección dentro de Socios:** selector "Mis socios | En clase" arriba de la pestaña Socios (gym-admin y gym-form).
  - El contenido actual de Socios pasa intacto a "Mis socios".
  - `memberSubsection` es un signal con la regla de apertura de la pregunta A.
  - Las calugas fuerzan "Mis socios".
- **Banner de recordatorio:** en el layout del panel, calculado con los horarios ya cargados (sin llamadas extra) cada 30 s.
- **Reutiliza:**
  - `WorkoutPlanModal`.
  - El marcado a mano de asistencia.
  - `whatsappUrl`.
  - `shortTime` / `formatIsoDate`.
- **Tour de la demo:** paso nuevo "Tu clase de ahora, con la memoria de cada alumno".

### Tests
- **Mockito:**
  - Elige la clase en curso o la siguiente.
  - Dos clases simultáneas.
  - Gym ajeno da 404.
  - "Qué le toca" por socio.
  - "Sin rutina".
  - "No anotó la última".
  - "Primera clase".
  - Número de consultas acotado (sin N+1).
- **Vitest:** orden de la lista y textos ("Le tocaba X pero hizo Y").
- **Prueba visual con el stub:** clase en curso con mezcla de casos, a 430 px y 1280 px.
- **Antes de subir:** skills de seguridad y performance (regla permanente).

## Fases sugeridas

1. **"En clase" dentro de Socios:** selector de clase, tarjetas, hoja de detalle y **"Anotar por él"**. Es lo que más valor da.
2. **Banner de recordatorio** en el panel, "Hoy te toca" en la app del socio y el botón "Anotar" después del QR.

## Preguntas pendientes

Ninguna. El plan está cerrado y listo para ejecutar.
