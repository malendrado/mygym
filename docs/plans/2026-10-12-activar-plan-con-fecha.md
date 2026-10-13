# Activar un plan con fecha de inicio y clases ya usadas, 2026-10-12

Plan para que lo ejecute otra sesión. **No implementar hasta que el usuario lo apruebe.**

## Decisiones del usuario (2026-10-12)

1. **Sin fechas futuras:** solo hoy o hacia atrás.
2. **Hasta un mes atrás** (más atrás el plan ya habría vencido).
3. **El email al socio** dice "vigente del X al Y" y cuántas clases le quedan.
4. **Solo admin del gym y super-admin** (el profesor no), como hoy.

## El pedido

Hoy "Marcar pago" (admin y super-admin) pide el plan y el banco, y el plan arranca **ahora**. En la vida real pasa que:

- **El socio pagó hace días o semanas:** transferencia, efectivo o un trato directo con el dueño.
- **Ya usó clases fuera de la app:** por ejemplo, vino 4 veces mientras el admin no lo había registrado, o hubo una emergencia.

El admin necesita activar el plan **con la fecha en que realmente empezó** y **descontando las clases que ya usó**. Así el vencimiento y el cupo quedan como corresponde.

**Ejemplo:** plan de 8 clases pagado hace 2 semanas, con 4 clases usadas fuera de la app. El plan queda vigente desde esa fecha hasta un mes después y le quedan 4 clases (menos las que ya tenga reservadas en la app en ese período).

## Lo que ya existe (verificado en el código)

- **El período sale de `paidAt`:**
  - `MemberService.currentPeriod` va de `paidAt` hasta el día anterior a `paidAt + 1 mes`.
  - `membershipStatus` (Activo, Por vencer, Vencido) y `periodEnd` usan el mismo dato.
  - **No hay otra fuente de verdad:** basta con guardar la fecha de inicio real en `paidAt` para que todo cuadre (estado, vencimiento, avisos y clases fijas).
- **"Clases usadas fuera de la app" ya existe para la importación por Excel:**
  - `AppUser.usedSessionsAtImport` (V21) se suma en `remainingClasses` a las reservas del período.
  - Se borra al renovar (`simulatePlanPayment` y Flow).
  - Es exactamente el dato que se necesita acá: **no hace falta columna nueva en el socio.**
- **`GymService.simulatePlanPayment`:**
  - Pone `paidAt = ahora` y `usedSessionsAtImport = null`.
  - Guarda un `ManualPayment` (V36) con el banco.
  - Manda los emails de "pago confirmado" (al socio y a los admins).
  - Dispara `MembershipPaidEvent`, que reserva las clases fijas del período.
- **Las clases fijas** se generan desde hoy hasta el fin del período y respetan el cupo restante (`remainingClasses`), así que ya descuentan las clases usadas fuera.
- **El modal `mark-paid-modal`** lo comparten gym-admin y gym-form (super-admin).

## Cómo funciona

### En el modal "Marcar pago"

Debajo del plan y el banco, dos datos nuevos:

1. **"¿Desde cuándo vale el plan?"**
   - Un selector de fecha que por defecto es **hoy**.
   - Atajos rápidos: **Hoy**, **Hace 1 semana** y **Hace 2 semanas**.
   - Solo fechas desde hoy hacia atrás, hasta un mes atrás. Con más de un mes el plan ya habría vencido, y se dice así.
2. **"¿Cuántas clases ya usó?"**, solo si el plan tiene cupo (los planes libres no lo muestran):
   - Un contador con − y +, de 0 al cupo del plan. Por defecto 0.
   - Texto de ayuda: "Clases que tomó fuera de la app desde esa fecha (sin reservar acá)."

**Resumen en vivo** antes de confirmar:

> **Vigente del 28-09-2026 al 27-10-2026**
> Plan Básico · 8 clases · ya usó 4 · ya tiene 1 reserva en la app en ese período → **le quedan 3**

- **"Ya tiene N reservas en la app"** sale del servidor. Ver el endpoint de vista previa en Cambios técnicos.
- **Aviso** si con esos datos le quedan 0 clases: "No podrá reservar más clases este período."
- **Aviso** si el plan queda "por vencer" (menos de 3 días): "Vence pronto: el socio recibirá el aviso de vencimiento."

### Qué pasa al confirmar

- `paidAt` = la fecha elegida (inicio del día en Chile; si es hoy, la hora actual como siempre).
- `usedSessionsAtImport` = las clases ya usadas (null si es 0).
- **`ManualPayment` guarda:**
  - **La fecha real del registro:** su `paidAt` sigue siendo el momento en que el admin lo marcó, para auditoría.
  - **La fecha de inicio del período y las clases usadas:** columnas nuevas, para los informes.
- **Emails de "pago confirmado":** dicen **"vigente del X al Y"** y, si hay clases usadas, **"te quedan N clases"**. Así el socio no se confunde si el plan "ya lleva dos semanas".
- **Clases fijas:** se reservan desde hoy hasta el fin del período, dentro del cupo que le queda (ya funciona así).

### Lo que NO cambia
- **Pago por Flow:** sigue arrancando ahora, sin clases usadas.
- **Importación por Excel:** ya trae fecha de pago y clases usadas por columna.
- **"Quitar plan":** sigue igual.

## Cambios técnicos

### Backend
- **`MarkPaidRequest`:** agrega `LocalDate startDate` (opcional; null = ahora) y `Integer usedClasses` (opcional, `@Min(0)`).
- **Validación en `GymService.simulatePlanPayment`** (400 con mensajes claros):
  - `startDate` no puede ser futura: "La fecha de inicio no puede ser futura."
  - `startDate + 1 mes` debe ser posterior a hoy: "Con esa fecha el plan ya habría vencido el {fecha}."
  - `usedClasses` ≤ cupo del plan. Si el plan es libre, se ignora (se guarda null).
- **`paidAt`:**
  - Si `startDate` es hoy, `Instant.now()` (igual que hoy).
  - Si es pasada, el inicio de ese día en `America/Santiago`. El vencimiento (`periodEnd`) queda en ese mismo día un mes después, a las 00:00.
- **Vista previa:** `GET /api/gym-admin/members/{id}/payment-preview?planId&startDate&usedClasses` (más la contraparte super-admin en `/api/gyms/{gymId}/...`).
  - Devuelve `{ periodStart, periodEnd, monthlyClasses, usedOutside, bookedInApp, remaining, expiringSoon }`.
  - Cuenta las reservas BOOKED del socio en ese rango (una consulta, `countByMemberIdAndStatusAndClassDateBetween`, que ya existe).
  - Solo lectura. Valida el socio y el plan del gym (404 si no).
- **V41** (V40 ya está tomada por "En clase"):

```sql
ALTER TABLE manual_payment ADD COLUMN period_start DATE;            -- inicio del período que activó este pago
ALTER TABLE manual_payment ADD COLUMN used_classes_outside INT;      -- clases que ya había usado fuera de la app
```

- **Emails:** `MemberLifecycleEmailService.sendPaymentConfirmedMember/Admin` reciben el período (inicio y fin) y las clases que le quedan, y las muestran con formato `dd/MM/yyyy`.
- **`AppUser.usedSessionsAtImport`:** se renombra solo en comentarios y documentación a "clases usadas fuera de la app (Excel o marcado manual)". La columna no cambia.

### Frontend
- **`mark-paid-modal`:**
  - Selector de fecha con atajos, contador de clases (solo planes con cupo) y resumen en vivo.
  - La vista previa se pide al cambiar plan, fecha o clases, con debounce de 300 ms.
  - Emite `{ planId, bank, startDate, usedClasses }`.
- **gym-admin y gym-form:** pasan los datos nuevos a `markMyMemberPaid` / `markMemberPaid`.
- **La lista de socios no cambia:** ya muestra "fecha de inicio → vencimiento" y "N/M sesiones" desde `paidAt` y `remainingClasses`, así que se actualiza sola.
- **Tour de la demo:** el paso de "Marcar pago" menciona la fecha de inicio y las clases usadas.

### Tests
- **Mockito (`GymServiceManualPaymentTest`):**
  - Fecha pasada: `paidAt` y período correctos.
  - Fecha futura: 400.
  - Fecha de hace más de un mes: 400.
  - `usedClasses` se guarda en `usedSessionsAtImport` y descuenta el cupo.
  - Plan libre ignora `usedClasses`.
  - `ManualPayment` guarda `period_start` y las clases usadas.
  - Sin los campos nuevos funciona igual que hoy.
- **Vista previa:** cuenta las reservas en el rango y devuelve socio de otro gym como 404.
- **Vitest:** el texto del resumen y los atajos de fecha.
- **Antes de subir:** skills de seguridad y performance (regla permanente).

### Casos borde

| Caso | Esperado |
|---|---|
| Fecha = hoy, 0 clases | Igual que hoy (sin cambios de comportamiento) |
| Plan de 8, fecha hace 2 semanas, usó 4, tiene 1 reserva en la app en el período | Vigente hasta dentro de ~2 semanas; le quedan 3 |
| Usó tantas clases como el cupo | Se permite; avisa "no podrá reservar más este período" |
| Fecha de hace 31 días | 400: "el plan ya habría vencido" |
| Plan libre | No se muestra el contador; `usedClasses` se ignora |
| Socio con plan vigente que renueva con fecha pasada | El período nuevo reemplaza al anterior (igual que hoy al renovar) |
| Fecha que deja el plan "por vencer" | Se permite; el socio recibe el aviso de vencimiento del job diario |

## Preguntas pendientes

Ninguna. El plan está cerrado y listo para ejecutar.
