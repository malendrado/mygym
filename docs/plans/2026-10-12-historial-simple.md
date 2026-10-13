# Historial más simple: primero la clase, el resumen después

Estado: PLAN CERRADO, SIN EJECUTAR (2026-10-12). Revisado el dato del QR: no requiere backend. Aplica a gym-admin y super-admin (gym-form).

## Problema
Para saber "quién vino a la clase X del día Y" hoy hay que pasar buscador + Semana/Mes + fecha + flechas, bajar por
todo el resumen (números, por clase, por día, 2 rankings), abrir el día y elegir entre "Ver quién reservó" (solo
reservas) o el modal "Asistencia" del día. 4–5 pasos con scroll, y "reservó" y "vino" en lugares distintos.

## Decisiones (con el usuario)
- Historial abre en **hoy**.
- Se **elimina el modal "Asistencia" del día**: todo queda en la lista desplegable de cada clase.
- El modo **Mes** vive **solo dentro de "Ver resumen"**. La vista principal es siempre por semana.

## Diseño
```
Historial                          🔍 Buscar socio
‹  Semana 12 – 18 oct  ›            (tocar el texto abre el calendario)
[Lu 12][Ma 13][Mi 14][Ju 15][Vi 16][Sá 17][Do 18]   ← hoy marcado
Martes 13
  07:00 Funcional        6 de 8 vinieron  ›
  19:00 CrossFit         8 de 10 vinieron ⌄
     ✓ Ana Soto          QR
     ✓ Pedro Rojas       a mano
     ✗ Luis Díaz         [Marcar que vino]
  20:00 GAP              aún no termina   ›
┌ Esta semana: 73% de asistencia ▲5%   Ver resumen › ┐
```
1. **Tira de días** (lunes–domingo) de la semana elegida; flechas cambian de semana; tocar la etiqueta abre el
   calendario (se quita el input de fecha suelto). Al abrir: semana actual, día de hoy.
2. **Clases del día** en lista: hora, nombre, "N de M vinieron" (o "aún no termina" / "en curso"). Tocar despliega el
   roster de quienes reservaron con ✓ vino (método: "QR" o "a mano"; `CheckinMethod` solo tiene QR y MANUAL, lo que marca el profe en "En clase" cuenta como a mano) / ✗ no vino / pendiente, y el botón
   "Marcar que vino / que no vino" dentro de la ventana de edición (7 días). Reemplaza "Ver quién reservó" y el modal
   "Asistencia".
3. **Línea de resumen** al final del día: % de asistencia de la semana + variación. "Ver resumen" abre la vista
   aparte (`app-attendance-summary`) con:
   - selector Semana / Mes y navegación del período;
   - la frase "Vinieron N de M (X%) ▲/▼";
   - una lista de socios con pestañas **Faltan / Vienen** (WhatsApp en cada fila, tocar = detalle del socio);
   - "Ver por clase y por día" plegado por defecto.
4. **Buscador de socio** se mantiene arriba; elegir un socio abre directo su detalle de asistencias (vista que ya
   existe en attendance-summary).

## Reutilización
- Roster + corrección: lógica de `attendance-modal` (setAttendance, ventana −10 min/+7 días) movida a un componente
  `class-roster` inline.
- Datos por día: los que ya carga `historyWeekSummary()` + attendees por bloque/fecha.
- Detalle por socio y listas paginadas: `attendance-summary` (ya existe).
- Backend: **sin cambios**. Verificado: `GET /api/gym-admin/history-attendees` y su contraparte super-admin
  `/api/gyms/{id}/history-attendees` devuelven por asistente `reservationId`, `checkedInAt` y `checkinMethod`
  (`AttendeeResponse`, mapeado en GymAdminController y GymController), y el modelo `Attendee` del frontend ya los
  tiene. Una sola llamada por semana (ya cacheada por rango en `loadHistoryAttendees`).
- Ojo: ese batch solo trae clases CON reservas; las clases sin reservas del día salen de los bloques de
  `historyWeekSummary()` y se muestran como "Sin reservas".

## Pruebas
- vitest: util de tira de días (semana de hoy, cruce de mes/año), estados de clase (terminó / en curso / futura).
- Visual con stub: gym-admin y super-admin, mobile 360 px y desktop; demo (`attendanceDemo`) sigue funcionando.
- Tour guiado: actualizar pasos `history-*` (se van `history-attendance` y `history-summary`, entran tira de días y
  "Ver resumen").

## Antes de subir
Skills de seguridad y performance (las 4), tests, commit sin Co-Authored-By, deploy solo si el usuario lo pide.
