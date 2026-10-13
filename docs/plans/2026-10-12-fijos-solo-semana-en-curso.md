# Socios con clases fijas: reservar y cancelar solo en la semana en curso

Estado: PLAN CERRADO, SIN EJECUTAR (2026-10-12).

## Regla (decidida con el usuario)
- Interruptor en **Horarios → Reglas de reserva**: "Socios con clases fijas: solo reservan y cancelan en la semana en curso". Apagado por defecto (nada cambia para los gyms actuales).
- Aplica **solo** a socios con clases fijas precargadas por el admin (`member_fixed_class` con al menos una fila). Al resto no le afecta.
- Con el interruptor encendido, ese socio solo puede **reservar** y **cancelar** clases cuya fecha cae en la semana actual: **lunes a domingo**, zona America/Santiago. Aplica a cualquier reserva suya (fija o no).
- Al cancelar, el cupo se devuelve (ya funciona así hoy: el cupo cuenta reservas BOOKED).
- No aplica al admin: `cancelAsAdmin` y lo que haga el admin/profesor por el socio siguen sin esta regla.
- La generación automática de clases fijas no se toca (sigue reservando todo el período).
- Las ventanas existentes (reserva/cancelación en minutos) siguen aplicando además de esta regla.

Ejemplo: hoy jueves 15-10. Puede cancelar su fija del vie 16 y reservar el sáb 17 o dom 18. No puede cancelar la fija del mar 20 ni reservar el lun 19 (se abre el lunes 19 a las 00:00).

## Backend
1. **V42**: `ALTER TABLE gym ADD COLUMN fixed_members_current_week_only boolean NOT NULL DEFAULT false;`
2. `Gym.fixedMembersCurrentWeekOnly`; `BookingRulesRequest` + `GymResponse` + `PublicGymResponse` (el socio lo necesita para la UI) con el campo nuevo (`@NotNull Boolean`, mismo patrón que `showAttendeesToMembers`). `GymService.updateBookingRules` lo guarda.
3. `MemberFixedClassRepository.existsByMemberId(Long)`.
4. `ReservationService`: helper `assertCurrentWeekIfFixed(gym, memberId, classDate)`; lanza `BookingWindowClosedException` (409, ya mapeado) con mensaje claro:
   - reservar: "Como tienes clases fijas, solo puedes reservar clases de esta semana (hasta el domingo 18-10)."
   - cancelar: "Como tienes clases fijas, solo puedes cancelar clases de esta semana (hasta el domingo 18-10)."
   - Llamarlo en `book()` (antes del resto de validaciones de cupo) y en `cancel()` (socio).
   - Lista de espera: unirse a la lista de una clase fuera de la semana también se rechaza (`WaitlistService.join`), para no prometer algo que no podrá reservar.
5. `MemberResponse`/respuesta de "mis datos" del socio: flag `hasFixedClasses` si no existe ya (revisar `MemberResponse`, que ya menciona fijas).

## Frontend
- `booking-rules-card`: nuevo toggle con texto de ayuda "Los socios con clases fijas solo podrán reservar y cancelar clases de la semana actual (lunes a domingo). Al cancelar recuperan el cupo para usarlo esa misma semana." Sirve para gym-admin y super-admin (componente compartido).
- `member`: si la regla está activa y el socio tiene fijas:
  - Clases de semanas siguientes: botón Reservar/Cancelar deshabilitado con el motivo "Disponible desde el lunes dd-MM".
  - Aviso corto arriba del calendario: "Tienes clases fijas: puedes cambiar solo las de esta semana."
  - El servidor manda igual; la UI solo evita el error.
- Tour demo: mencionar la regla en el paso de Reglas de reserva.

## Pruebas
- Backend (`ReservationServiceWindowsTest` o nuevo): interruptor apagado = sin cambios; encendido + sin fijas = sin cambios; encendido + fijas: reservar/cancelar esta semana OK, la próxima rechazado, domingo 23:59 vs lunes 00:00, `cancelAsAdmin` sin restricción, cupo devuelto al cancelar, lista de espera fuera de semana rechazada.
- Frontend: util de semana (lunes–domingo) con spec; revisión visual en el stub.

## Antes de subir
Skills de seguridad y performance (las 4), tests, commit sin Co-Authored-By, deploy solo si el usuario lo pide.
