# Pestaña "Clases": Agenda · Asistencia · Horarios

Estado: IMPLEMENTADO el 2026-10-13, compilado y verificado en local, SIN commit y SIN deploy. Ajuste posterior pedido por el usuario: los bloques NO llevan ningún acceso a asistentes (se quitó "Ver en la agenda"). Aplica a los dos paneles: dueño (`gym-admin`) y super-admin (`gym-form`).

## Problema
El admin no tiene una forma clara de ver quién reservó una clase futura (ej. hoy martes, el martes de la próxima
semana) ni cuántos cupos quedan:
- **Horarios** → "Ver quién reservó" muestra solo la PRÓXIMA ocurrencia de cada bloque (`nextOccurrenceDate()`), sin
  elegir fecha.
- **Historial** (`history-board`) sí navega cualquier semana (pasada o futura), pero el nombre no invita a buscar
  "lo que viene", no muestra capacidad ("8/12"), no muestra cierres de emergencia y el roster está plegado (hay que
  tocar cada clase para verlo).

## Decisiones (con el usuario)
1. La pestaña **"Horarios"** pasa a llamarse **"Clases"** y se divide en 3 sub-pestañas, en este orden:
   - **Agenda** (por defecto al entrar): día a día, cupos y quién reservó.
   - **Asistencia**: el consolidado semana/mes (vinieron / no vinieron / sin marcar, % por clase y por socio).
   - **Horarios**: lo que hoy es la pestaña Horarios (tarjeta "Reglas de reserva" + bloques).
2. La pestaña **"Historial" desaparece** del nivel superior. Nada se pierde: el día a día va a Agenda, el resumen a
   Asistencia, el buscador de socio a Agenda.
3. **El roster va SIEMPRE visible** (requisito explícito del usuario): cada clase muestra la lista de quienes
   reservaron directamente, sin acordeón, sin tener que presionar nada.
4. Se muestran también las clases **sin reservas** ("0/12 · Sin reservas").
5. Sin calendario mensual: la tira de días + selector de fecha que ya existen bastan.

## Diseño

```
[General] [Clases] [Planes] [Socios] [Mi marca] [Pantallas] [Cierres]      ← sin "Historial"

Clases:   ( Agenda )  ( Asistencia )  ( Horarios )                         ← sub-pestañas

Agenda
  🔍 Buscar socio por nombre...
  ‹  Semana 13 – 19 oct  ›                (tocar el texto abre el calendario)
  [Lu 13][Ma 14][Mi 15][Ju 16][Vi 17][Sá 18][Do 19]
  Martes 14 de octubre                                         Ir a hoy
  ┌──────────────────────────────────────────────────────────────────┐
  │ 07:00 · Funcional · Prof. Ana          ████████░░  8/12 cupos    │
  │   (foto) Ana Soto        Sin marcar                         [x]  │
  │   (foto) Pedro Rojas     Sin marcar                         [x]  │
  │   ...                                                            │
  ├──────────────────────────────────────────────────────────────────┤
  │ 19:00 · CrossFit                       ██████████ 12/12  LLENA   │
  │                                         3 en espera              │
  │   ...lista...                                                    │
  ├──────────────────────────────────────────────────────────────────┤
  │ 20:00 · GAP                            CERRADA · Corte de luz    │
  ├──────────────────────────────────────────────────────────────────┤
  │ 21:00 · Yoga                           0/10 · Sin reservas       │
  └──────────────────────────────────────────────────────────────────┘
  ┌ Esta semana: 42 de 50 vinieron · 84%            Ver asistencia › ┐

  Clase pasada: la cabecera muestra "7 de 10 vinieron · 70%" (como hoy) y cada persona ✓ Vino · 19:04 · QR /
  ✗ No vino, con "Marcar que vino / que no vino" dentro de la ventana de edición, y WhatsApp a quien faltó.
```

Roster siempre abierto:
- Mobile (≤ 600px): filas compactas (avatar 28px + nombre + estado + acciones), una columna.
- Desktop: las personas de cada clase en grilla de 2–3 columnas (`grid-template-columns: repeat(auto-fill, minmax(260px, 1fr))`).
  Ver regla [admin desktop ancho completo] en la memoria del usuario: nada de columna angosta centrada.
- Orden dentro de cada clase: igual que hoy (los no marcados primero, luego por nombre).

## Backend

### 1. Endpoint nuevo `GET /api/gym-admin/gym/agenda?from=&to=`
- `GymAdminController` (gymId desde el JWT, mismo patrón que `myHistoryAttendees`, línea ~103).
- Espejo super-admin en `GymController`: `GET /api/gyms/{id}/agenda?from=&to=` (al lado de `/{id}/history-attendees`, línea ~209).
- Permisos: ya cubiertos por `SecurityConfig` — el GET en `/api/gym-admin/**` admite `GYM_ADMIN`, `DEMO_ADMIN`, `PROFESOR`;
  `/api/gyms/**` es solo `SUPER_ADMIN`. No tocar `SecurityConfig`.
- Validar rango: `from <= to` y como máximo 31 días (400 con mensaje en español si no). La UI pide de a 7.

Respuesta (record nuevo `AgendaOccurrenceResponse` en `models/dto`), UNA entrada por cada ocurrencia (bloque × fecha
que coincide con su `dayOfWeek`) — **incluidas las que no tienen reservas**:
```java
public record AgendaOccurrenceResponse(
    Long gymBlockId, LocalDate classDate,
    int capacity, int taken,
    boolean closed, String closedReason,   // closedReason null si no está cerrada
    int waitlistCount,
    List<AttendeeResponse> attendees) {}
```
Bloques incluidos: activos, más los inactivos que tengan reservas en esa fecha (mismo criterio que `blocksOn()` del
front, para no perder historial).

### 2. Servicio `ReservationService.getAgenda(gymId, from, to)`
Todo en lote, sin N+1 (pool Hikari de 5 conexiones; ver comentario de `getOccurrenceAttendeesForRange`):
- Bloques: `gymBlockRepository.findByGymId(gymId)`.
- Reservas BOOKED del rango: `findByGymBlockIdInAndClassDateBetweenAndStatus` (ya existe) → agrupar con
  `groupByOccurrence` (ya existe) → `taken = attendees.size()`.
- Cierres: `GymClosureService.scheduleFor(gymId, from, to)` → `isClosed(blockId, date)`. Para el motivo, llamar
  `closedReasonFor(gymId, blockId, date)` SOLO en las ocurrencias cerradas (son pocas). Si se prefiere, extender
  `ClosureSchedule` para guardar el motivo y evitar esas llamadas — opcional.
- Lista de espera: agregar a `ClassWaitlistEntryRepository` el derivado
  `List<ClassWaitlistEntry> findByGymBlockIdInAndClassDateBetween(Collection<Long> blockIds, LocalDate from, LocalDate to)`
  y contar por `(blockId, date)` en memoria. Revisar si `ClassWaitlistEntry` tiene estado (avisado/expirado); contar
  solo las entradas vigentes con el mismo criterio que usa `WaitlistService`.
- Mapear asistentes con el mismo `toBatchResponse`/`AttendeeResponse` que ya usa history-attendees (incluye
  `reservationId`, `checkedInAt`, `checkinMethod`, `phone`, `photoUrl`, `email`).
- Ordenar por fecha y hora de inicio.

**No borrar** `history-attendees` ni `reservations/search`: el buscador sigue usando el formato
`BlockOccurrenceAttendeesResponse`, y `attendance-summary` puede depender de history-attendees (verificar con grep
antes de tocar nada).

### 3. Tests (Mockito, patrón `ReservationServiceWindowsTest`)
`ReservationServiceAgendaTest`:
- ocurrencia sin reservas aparece con `taken=0` y `attendees=[]`;
- `taken` cuenta solo BOOKED; capacity viene del bloque;
- bloque cerrado por cierre de día completo y por cierre de bloque puntual → `closed=true` + motivo;
- `waitlistCount` correcto por ocurrencia;
- bloque inactivo sin reservas no aparece; inactivo con reservas sí;
- rango > 31 días → excepción.
(Los tests de contexto Spring no corren sin base; la red real son los Mockito.)

## Frontend

### 4. Modelo y servicio
- `core/models/gym.model.ts` (o donde viva `BlockOccurrenceAttendees`): interfaz `AgendaOccurrence` espejo del record.
- `core/services/gym.service.ts`: `getMyAgenda(from, to)` y `getAgenda(gymId, from, to)`, junto a `getMyHistoryAttendees`
  (línea ~168).

### 5. `history-board` → Agenda (`app/src/app/pages/admin/gyms/history-board/`)
Se reusa el componente (no crear otro). Cambios:
- `loadWeek()` pide `getMyAgenda`/`getAgenda` en vez de history-attendees. Guardar por clave `blockId_date` la
  ocurrencia completa (capacity, taken, closed, closedReason, waitlistCount, attendees). Mantener caché por rango y
  `refreshToken`.
- `ClassRow` suma `capacity`, `taken`, `closed`, `closedReason`, `waitlistCount`, `full = taken >= capacity`.
- `blocksOn()` / `days().hasClasses`: seguir usando los bloques del input para pintar la tira aunque la llamada aún no
  vuelva; cuando vuelva, la respuesta manda.
- **Eliminar el acordeón**: quitar `expandedBlockId`, `toggleClass`, el `<button class="hb-class__head">` con chevron y
  el `@if (expandedBlockId() === row.block.id)`. La cabecera pasa a ser un `<div>` y la `<ul class="hb-people">` se
  renderiza siempre que `row.attendees.length > 0`.
- Cabecera de cada clase: hora · nombre · profesor + medidor de cupo:
  - barra delgada (`<div class="hb-meter"><span [style.width.%]="...">`) + texto `8/12 cupos` (Space Mono, ver tokens);
  - chip `Llena` cuando `full`; chip `N en espera` si `waitlistCount > 0`; chip `Cerrada · motivo` si `closed`
    (en ese caso no mostrar barra);
  - en clase terminada se mantiene el texto actual "N de M vinieron · X%"; en curso, "En curso · N de M marcaron".
- Colores: barra con `--brand-accent`; chips de texto con `--brand-accent-text-safe` (nunca el acento crudo como texto,
  ver gotcha de contraste en la skill mygym). "Cerrada" con `--ion-color-danger`. Probar en modo claro y oscuro.
- `hb-hint` ("Podrás marcar asistencia desde 10 min antes…", "Solo lectura…") se muestra una vez bajo la cabecera de
  la clase, no por persona.
- Quitar del componente la vista `'summary'` (se va a la sub-pestaña Asistencia, punto 6). La línea inferior queda como
  "Esta semana: N de M vinieron · X%" + "Ver asistencia ›", que emite un `@Output() openAttendance` con el ancla de la
  semana; el padre cambia de sub-pestaña.
- Desktop: `.hb-people` en grilla `repeat(auto-fill, minmax(260px, 1fr))`; mobile una columna.
- Las clases sin reservas: cabecera con `0/12 · Sin reservas`, sin lista.

### 6. Sub-pestaña Asistencia
- Mover el bloque `@if (view() === 'summary')` de `history-board.html` (selector Semana/Mes + navegación +
  `<app-attendance-summary>`) a un componente chico nuevo `attendance-tab` (misma carpeta `admin/gyms/`) o, si es más
  simple, dejarlo inline en el padre. Recibe `gymId`, `demo` y un `anchor` inicial (para que "Ver asistencia" abra en
  la semana que se estaba mirando). Sin cambios en `attendance-summary`.
- Mantener `(attendanceChanged)` → subir `historyRefresh` para que la Agenda recargue.

### 7. Padres: `gym-admin` y `gym-form` (en paridad, implementaciones paralelas)
- Tipo `Section`: renombrar `'blocks'` → `'classes'` y quitar `'history'`. `SECTION_LABELS`: `classes: 'Clases'`.
  Buscar con grep todos los `'blocks'`/`'history'` usados como sección (`setSection(...)`, tours, links internos) y
  actualizarlos — ojo, `'blocks'` también aparece como palabra en otros sentidos.
- Nuevo signal `classesSub = signal<'agenda' | 'attendance' | 'schedule'>('agenda')`. Al entrar a Clases
  (`setSection('classes')`) siempre cae en `'agenda'`.
- Segment de arriba: el botón "Horarios" (icono `time-outline`) pasa a "Clases" (icono `calendar-outline`); se elimina
  el botón "Historial". El PROFESOR sigue viendo Clases (hoy ve Horarios e Historial).
- Debajo del header de la sección, un segmento secundario con 3 opciones — reutilizar el patrón visual que ya usa la
  pestaña Socios para su sub-vista `memberSub` ('members' | 'board'), para que se vea igual.
- Contenido:
  - `agenda`: buscador de socio (el `ion-item.history-search-item` + resultados actuales, tal cual) + `<app-history-board>`.
  - `attendance`: la vista del punto 6.
  - `schedule`: el contenido actual de `section() === 'blocks'` (tarjeta `app-booking-rules-card` + bloques), sin cambios
    salvo el punto siguiente.
- En las tarjetas de bloque, "Ver quién reservó · fecha" deja de expandir una lista: pasa a ser un link
  "Ver en la agenda · mar 14 oct" que hace `classesSub.set('agenda')` y le pasa al board la fecha
  (`nextOccurrenceDate(block.dayOfWeek)`) por un `@Input() focusDate` (al cambiar: `anchor` y `selectedDate` a esa
  fecha y scroll a la clase). Después de esto, borrar el código muerto: `toggleAttendees`, `toggleAttendeesFor`,
  `attendeesFor`, `isBlockAttendeesExpanded`, `isBlockAttendeesLoading`, `expandedAttendeesKey`, `loadingAttendeesKey`
  y el HTML del `attendees-list` dentro de la tarjeta — **solo si no lo usa otra parte** (grep: el buscador de reservas
  usa `cancelReservation` y la clase CSS `attendees-list`, no los borres).
- `gym-form` (super-admin) tiene además la sección `'flow'` y en Socios (línea ~1026) otro `history-search-item`:
  revisar qué es antes de tocarlo; no forma parte de este cambio salvo que dependa de `'history'`.

### 8. Tour guiado (`sectionTours` en gym-admin.ts ~línea 1000)
- Mover el tour de `history` a la sección `classes`. **Mantener el código `'A_HISTORY'`** (o agregar uno nuevo también en
  `DemoTourService.ALLOWED_TOURS` del backend — deben coincidir exactamente). Más simple: el tour de Clases reutiliza
  `A_BLOCKS` para la sub-pestaña Horarios y `A_HISTORY` para Agenda; `currentSectionTour` elige según `classesSub()`.
- Textos nuevos (español neutro, cero voseo):
  - Agenda: "Mira quién viene a cada clase, cualquier día: hoy, mañana o la próxima semana."
  - Cupos: "Cada clase muestra sus cupos ocupados, si está llena, si hay gente en espera o si está cerrada."
  - Roster: "Debajo de cada clase está la lista de quienes reservaron. En clases pasadas ves quién vino y puedes corregirlo."
  - Asistencia: "El consolidado de la semana o el mes: quiénes vienen, quiénes faltan y el % por clase."
- Ajustar los `data-tour` (`history`, `history-search`, `history-week-nav`, `history-classes`, `history-summary-line`)
  para que sigan apuntando a elementos existentes.

### 9. Otros lugares que mencionen "Historial"
`grep -rn "Historial" app/src` y revisar: textos de ayuda, guía del socio/admin (`pages/guide`), FAQ, brochure
(`vercel-landing/brochure-src`), banner de bienvenida (`ADMIN_TIPS`). Cambiar a "Clases → Agenda" o
"Clases → Asistencia" según corresponda.

## Pruebas
- Backend: `ReservationServiceAgendaTest` (punto 3). `JAVA_HOME` al JDK 21 antes de `mvn` (ver skill mygym).
  Correr `mvn clean test -Dtest='*ServiceTest,*WindowsTest,*HeadStartTest,*AgendaTest'` o equivalente.
- Front: vitest para el armado de `ClassRow` (full, closed, sin reservas, waitlist) y para que "Ver en la agenda" ponga
  la fecha correcta. `ng build landing` **y** `ng build app` (budgets compartidos).
- Visual (ver memoria "Vista previa y verificación visual de mygym": servidor con API simulada + Playwright):
  360px y desktop 1440px; gym-admin, gym-form (super-admin), rol PROFESOR y cuenta demo (DEMO_ADMIN, solo lectura);
  tema oscuro y claro; día con clase llena, cerrada, con espera, sin reservas, pasada y futura (próxima semana).
- Medir que en desktop un día con 6 clases × 10 personas se lea sin scroll horizontal y sin columna angosta centrada.

## Reglas del proyecto (no negociables)
- Todo texto visible en español neutro chileno, **cero voseo** (grep de `tenés|podés|elegí|mirá|tocá|agregá|querés`
  antes de dar por terminado).
- Excepciones nuevas del backend con mensaje en español.
- Íconos nuevos: registrarlos con `addIcons()` en el archivo que los usa.
- Acciones sin efecto visible → toast de confirmación.
- Commits sin `Co-Authored-By`.
- **No deployar.** Antes de cualquier deploy (lo pide el usuario aparte): correr las skills de seguridad y performance.
