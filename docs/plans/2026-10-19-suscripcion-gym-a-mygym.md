# Suscripción del gym a mygym (cobro mensual, avisos, suspensión y "Renovar")

Estado: IMPLEMENTADO en local (2026-10-19), SIN commit ni deploy. Migración V55.

## Cómo quedó implementado

- Backend: `GymBilling` (única fuente de fechas, estados y montos), `GymSubscriptionService` (activar, pagos, transferencias, anular, cambio de plan), `GymSubscriptionFlowService` (Flow propio de mygym), `GymSubscriptionJob` (09:00 Chile), `GymBillingNotifier` (correo + push), `MygymPlanService`. La pausa ahora tiene motivo (`GymPauseGuard.Reason`: MANUAL o BILLING) y `/api/gym-status` devuelve `pauseReason`.
- Endpoints: `/api/gym-admin/subscription` (admin del gimnasio), `/api/gyms/{id}/subscription` (super admin), `/api/mygym-plans` (super admin) y `/api/public/mygym-plans` (landing y FAQ). Webhook `/api/public/flow/mygym-webhook`.
- Front: tarjeta compartida `app-gym-subscription` (admin y super admin, también dentro del aviso de pausa), pantalla "Planes" del super admin (`/admin/gyms/plans`), landing y FAQ leen los planes de la base, y "Renovar" del socio limitado a los 3 últimos días y al mismo plan.
- El interruptor `MYGYM_ADVANCE_PLANS` ahora solo controla el cambio de plan programado de un socio (sigue apagado); la renovación del mismo plan ya no depende de él.

## Para desplegar

Variables de Railway: `MYGYM_FLOW_API_KEY`, `MYGYM_FLOW_SECRET_KEY` (cuenta Flow de mygym; sin ellas el pago con Flow queda desactivado y solo hay transferencia), `MYGYM_BANK_HOLDER`, `MYGYM_BANK_RUT`, `MYGYM_BANK_NAME`, `MYGYM_BANK_ACCOUNT_TYPE`, `MYGYM_BANK_ACCOUNT_NUMBER` (y opcional `MYGYM_BANK_EMAIL`). La migración V55 deja a todos los gimnasios existentes activos desde el día del deploy, y exentos los que tengan "demo", "prueba" o "test" en el nombre o slug. Los gimnasios con más de 200 socios quedan en Escala sin precio: hay que fijarles el precio en su ficha antes de que paguen.


## Objetivo

El dueño del gym paga a mygym una mensualidad según su plan (Empieza, Crece o Escala). El super admin define la fecha de inicio; desde ahí se cobra mes a mes con el mismo día de corte. Si no paga, tras 2 días de gracia el gym queda suspendido con el mismo efecto que la pausa actual, hasta que pague con Flow (reactivación automática) o el super admin confirme la transferencia. En los 3 últimos días del período se habilita "Renovar", tanto para el gym como para sus socios.

## Decisiones tomadas

| # | Tema | Decisión |
|---|------|----------|
| 1 | Planes | Los mismos 3 de la landing. Escala tiene precio a medida por gym. |
| 2 | Cupo de socios | Lo fija el plan automáticamente; el super admin puede sobrescribirlo. |
| 3 | Factura | La emite Cortes Dev a mano (fuera del sistema). |
| 4 | Comisión Flow | La paga el gym cuando paga con Flow. También puede transferir, y el super admin puede activar el plan a mano. |
| 5 | Cuenta Flow | mygym tiene cuenta propia; se usa para este cobro, separada de las cuentas Flow de cada gym. |
| 6 | Período | Mes calendario desde la fecha de inicio. |
| 7 | Pago atrasado | Mantiene el día de corte. |
| 8 | Gyms existentes | Entran con inicio = fecha del deploy; el super admin puede cambiar esa fecha. |
| 9 | Exentos | Existe la opción "exento" (demo, cortesía). |
| 10 | Gracia | 2 días después del vencimiento; luego se suspende. |
| 11 | Efecto de la suspensión | El mismo que la pausa actual (blur para admins, profes y socios; TV en pausa; sin avisos automáticos). |
| 12 | Socios con plan pagado | **No** se congelan (revisado después; ver decisión 13). |
| 13 | Pagar estando bloqueado | El blur del admin muestra "Pago pendiente" con "Pagar con Flow" y "Ya transferí". |
| 14 | Motivo | Se distingue "pausado por el super admin" de "suspendido por no pago" (textos y reactivación distintos); el efecto es el mismo. |
| 15-16 | Transferencia | El admin avisa "Ya transferí", el super admin confirma y eso reactiva. Flow reactiva automáticamente. |
| 17 | Canal | Push + correo (sin SMS ni WhatsApp). |
| 18 | Destinatarios | Todos los GYM_ADMIN del gym (push a quienes tengan la app instalada). |
| 19 | Super admin | Recibe avisos de vencimiento, pago, "ya transferí" y suspensión. |
| 20-21 | Renovar | Visible desde 3 días antes del vencimiento y también una vez vencido. Suma un mes sin perder días. |
| 22 | Cambio de plan | El gym solo puede renovar el mismo plan; el cambio lo hace el super admin. |
| 23 | Socios | La ventana de 3 días aplica a Flow y a transferencia. |
| 24 | Permanencia mínima | Fuera del sistema; se maneja en persona. |
| 25 | Cambio de plan del gym | Rige desde el próximo período, sin prorrateo. |
| 26 | Historial de pagos | Visible para el admin del gym y para el super admin. |

## Dudas que aún quedan abiertas

Ninguna; ver "Decisiones finales" al final.

## Línea de tiempo de un período (día de corte = 15)

| Día | Qué pasa |
|-----|----------|
| 12, 13, 14 | Aviso diario (push + correo) "tu plan mygym vence en N días". Aparece "Renovar". |
| 15 (vence) | Aviso "vence hoy". Al super admin le llega un correo. |
| 16, 17 | Gracia: banner rojo en el panel del admin y aviso diario "se suspende en N días". |
| 18 (09:00) | Suspensión por no pago: blur para todos, aviso a admins y al super admin. |
| Paga | Flow lo reactiva al instante; con transferencia, al confirmar el super admin. |

## Etapas

### Etapa 1: modelo y migración (V55)

Tablas nuevas (todas con RLS habilitado y agregadas a `MigrationRlsTest`; ver la regla de auditar RLS de todas las tablas):

- `mygym_plan`: el catálogo que alimenta la landing y el FAQ (ver Etapa 0). Columnas: `code`, `name`, `tagline`, `price_clp` (neto, sin IVA; null = "A medida"), `max_users` (null = sin límite), `max_profesores`, `max_admins`, `features` (jsonb, lista de textos de la tarjeta), `highlighted`, `sort_order`, `active` (visible en la landing y asignable). La carga inicial siembra los 3 planes actuales (Empieza, Crece, Escala) con los mismos textos que hoy están escritos a mano en `landing-pricing.ts`, con 10 profesores y 5 admins.
- `gym_subscription` (1 a 1 con gym; `gym_id` PK/FK):
  - `plan_id`, `price_override_clp`, `max_users_override` (null = usa los del plan).
  - `anchor_date` (fecha de inicio, define el día de corte), `paid_periods` (meses cubiertos desde el ancla), siguiendo la misma lógica que `MembershipPeriods`.
  - `pending_plan_id` (cambio de plan que rige desde el próximo período).
  - `exempt` boolean.
  - `billing_status`: NOT_STARTED, ACTIVE, GRACE, SUSPENDED, EXEMPT (calculado por el cron y persistido para que el filtro de pausa lo lea barato).
  - `suspended_at`.
- `gym_subscription_payment`: `gym_id`, `method` (FLOW, TRANSFER, MANUAL), `status` (PENDING, PAID, REJECTED, REPORTED), `amount_clp`, `iva_clp`, `flow_fee_clp`, `period_start`, `period_end`, `flow_token`, `reported_at`, `reported_by`, `confirmed_at`, `confirmed_by`, `note`. Índices: `(gym_id, created_at desc)` para el historial y uno parcial en `flow_token` para el webhook.
- `gym_suspension`: `gym_id`, `started_at`, `ended_at`, `reason` (BILLING o MANUAL). Sirve para auditar.

Datos iniciales: todos los gyms existentes entran con `anchor_date = fecha del deploy`, plan según su `maxUsers` actual (≤100 Empieza, ≤200 Crece, más = Escala) y `paid_periods = 1`. Demo y gyms de prueba quedan con `exempt = true` (el super admin lo ajusta).

Los límites de profesores y admins, hoy fijos en `GymCapacityService` (10 y 5), pasan a leerse del plan del gym; con la carga inicial, el resultado es el mismo.

### Etapa 0: landing y FAQ desde los planes

- `GET /api/public/mygym-plans` (público, con caché corta) devuelve los planes activos ordenados.
- `landing-pricing.ts` deja de tener los planes escritos a mano y los pide a ese endpoint. Si la API falla, se muestra un respaldo mínimo ("Escríbenos para conocer los precios") en vez de precios viejos.
- La respuesta del FAQ sobre precios (`faq.ts`) se arma con los mismos datos.
- Super admin: nueva pantalla "Planes mygym" para crear, editar, ordenar y desactivar planes. Un cambio de precio rige para cada gym desde su próximo período.
- Quedan fuera los contratos en `docs/comercial` (se actualizan a mano).

### Activación de un gym nuevo

- El gym se crea en estado NOT_STARTED: queda pausado (mismo blur) hasta que se active.
- El primer pago lo cobras por fuera (link de pago de Flow generado en el panel de Flow, o transferencia).
- Al recibirlo, "Activar" pide: plan (precio a medida si es Escala), fecha de inicio (por defecto hoy, editable) y el registro de ese primer pago (método, monto y número de operación). Eso crea la suscripción con `paid_periods = 1` y despausa el gym.
- Si el super admin pone una fecha de inicio futura, el gym queda activo y no paga hasta esa fecha.

### Etapa 2: suspensión con motivo

- `GymPauseGuard` pasa a considerar "pausado" si `Gym.active = false` **o** `billing_status = SUSPENDED`, y expone el motivo.
- `/api/gym-status` devuelve `pauseReason` (MANUAL o BILLING).
- `GymPauseFilter`: estando SUSPENDED por no pago, se deja pasar solo a GYM_ADMIN en `/api/gym-admin/subscription/**` (ver suscripción, pagar con Flow, "Ya transferí").
- Blur (`gym-pause`): para BILLING, el admin ve "Pago pendiente" con el monto y los dos botones; profes y socios ven el mismo texto de pausa de hoy.
- Al suspender se abre una fila en `gym_suspension` y al reactivar se cierra. Los planes de los socios no se tocan.
- Las clases fijas, los recordatorios y la TV ya se pausan porque leen el mismo guard.

### Etapa 3: cron de suscripciones

`GymSubscriptionJob` a las 09:00 hora de Chile (mismo patrón que `MembershipReminderJob`), con inserción idempotente para no avisar dos veces el mismo día:

- -3, -2, -1: aviso "vence en N días".
- 0: "vence hoy" + correo al super admin.
- +1, +2: GRACE, "se suspende en N días".
- +3: SUSPENDED + avisos.
- Aplica el `pending_plan_id` al empezar el período nuevo.
- Salta los exentos.
- Gym pausado a mano: el reloj sigue corriendo (puede pasar a GRACE o SUSPENDED), pero no se envían avisos mientras dure la pausa manual. Al despausarlo, el super admin decide si le corre la fecha.

Correos nuevos en un `GymBillingEmailService`; textos push en `PushMessages`. El super admin no tiene push, así que a él solo le llegan correos.

### Etapa 4: pago con Flow de mygym

- Configuración nueva `mygym.flow.api-key/secret` (variables en Railway), separada de las credenciales de cada gym.
- `POST /api/gym-admin/subscription/checkout`: monto = precio neto + IVA + comisión Flow (la paga el gym). Crea un `gym_subscription_payment` PENDING.
- Webhook propio `POST /api/public/flow/mygym-webhook`: vuelve a consultar `getStatus` (igual que hoy), marca PAID, suma un período manteniendo el día de corte, reactiva y avisa al super admin para que emita la factura.
- Pantalla de retorno al panel del admin con un toast de confirmación.

### Etapa 5: transferencia y acciones del super admin

- Admin: "Ya transferí" (fecha, banco, número de operación opcional) crea un pago REPORTED y le envía un correo al super admin. El gym sigue suspendido hasta que se confirme.
- Super admin, en la ficha del gym, sección "Suscripción mygym":
  - Plan, precio a medida, cupo (con sobrescritura), fecha de inicio editable, exento.
  - Confirmar o rechazar los "Ya transferí".
  - "Marcar pago" manual (sin aviso previo).
  - Cambiar de plan (rige desde el próximo período).
  - Historial de pagos.
- Se mostrarán los datos bancarios de mygym para transferir (configurables, no escritos en el código).

### Etapa 6: panel del admin, "Mi suscripción mygym"

- Tarjeta con el plan, el precio, la fecha de vencimiento, el estado y el historial de pagos.
- "Renovar" (Flow o "Ya transferí") visible solo desde 3 días antes del vencimiento, en gracia o suspendido. Renovar el mismo plan suma un mes sin perder días.
- Banner rojo durante la gracia.

### Etapa 7: "Renovar" de los socios en los últimos 3 días

- Se reemplaza el interruptor global `MYGYM_ADVANCE_PLANS` por la regla de la ventana: `canExtend` es verdadero solo si faltan ≤3 días para el vencimiento (o ya venció) **y** el plan es el mismo.
- `isPlanChange` sigue apagado para los socios (el cambio de plan de un socio queda como hoy, hecho por el admin del gym con "Marcar pago").
- `canRenewEarly` en `MemberService` usa la misma regla, y el botón "Renovar" en `/member` aparece solo en esa ventana. Aplica a Flow y a transferencia.
- "Marcar pago" del admin del gym (`simulatePlanPayment`): si el socio tiene plan vigente del mismo plan, **siempre** suma el mes al final, sin ventana; el socio nunca pierde días. La ventana de 3 días limita solo el botón del socio.

### Etapa 8: pruebas y deploy

- Tests unitarios de la línea de tiempo (cortes de fin de mes: 31 → 30/28), de la suspensión y reactivación, del webhook idempotente, del filtro con el motivo BILLING y de la ventana de "Renovar".
- E2E con el stub de mygym: admin suspendido paga, el blur desaparece.
- Antes del deploy: las 4 skills de seguridad y performance sobre el diff.

## Decisiones finales (2026-10-19)

1. **Pago después de una suspensión:**
   - Si paga **antes del siguiente día de corte**, cubre el mes que debía y se reactiva al instante. Los días suspendidos los pierde (son su atraso). Ejemplo: vence el 15/11, se suspende el 18/11, paga el 20/11 y cubre del 15/11 al 15/12.
   - Si paga **después del siguiente corte**, cubre desde el próximo corte posterior al pago y lo intermedio queda regalado. Ejemplo: vence el 15/11, paga el 3/1 y cubre del 15/1 al 15/2.
   - Así nadie gana días dejándose suspender.
2. **Congelamiento:** descartado (ver decisión 13).
3. **"Ya transferí":** fecha, banco y número de operación, sin adjuntar comprobante.
4. **Cuenta para transferir:** la de Cortes Dev, configurable.
5. **Alcance del super admin:** solo actúa sobre la suscripción del gym, nunca sobre los planes de los socios. El cambio de plan de un socio sigue siendo del admin del gym ("Marcar pago").

6. **Activación:** el super admin activa un gym cuando ya le pagó el primer mes (link de Flow o transferencia, por fuera del sistema). Ver "Activación de un gym nuevo".
7. **Pausa manual:** el reloj de la suscripción sigue corriendo; no se envían avisos mientras dure.
8. **"Marcar pago" a socios:** nunca hace perder días; siempre suma al final.
9. **Landing y FAQ:** se arman desde los planes de mygym (Etapa 0), sembrados con los 3 actuales.

Supuestos aceptados:
- Bajar de plan con más socios que el nuevo cupo no borra a nadie; solo bloquea socios nuevos.
- Escala no se guarda sin precio a medida.
- Renovar usa el plan pendiente si existe.
- Socio vencido elige cualquier plan.
- Cualquier admin del gym puede pagar.
- Los gyms exentos no ven la suscripción ni reciben avisos.
- El super admin puede anular un pago confirmado por error.

Decisiones de la tercera revisión:
10. **Datos de facturación del gym:** nueva sección (RUT, razón social, giro, dirección, correo de facturación) en `gym_subscription` o en una tabla `gym_billing_info`. Se pide al activar, se prellena con los datos `flowCompany*` si existen y el admin puede editarla en "Mi suscripción". En cada pago confirmado le llega un correo a contacto@mygym.cl con esos datos para emitir la factura.
11. **Reservas durante la suspensión:** no se tocan y siguen contando como usadas, porque el gym puede seguir haciendo clases aunque la app esté bloqueada.
12. **Cambio de precios de planes:** fuera del alcance por ahora; sin aviso automático. Queda pendiente para después.
13. **Sin congelamiento:** los días de los socios no se congelan durante la suspensión, porque el gym puede seguir haciendo clases. No existe `frozen_days`.

Detalles resueltos:
- Un plan desactivado sale de la landing y no se asigna a gyms nuevos; los gyms que ya lo tienen lo mantienen.
- Los avisos al super admin van a contacto@mygym.cl. Los datos bancarios de Cortes Dev van en variables de entorno.
- Una transferencia rechazada se avisa al admin por push y correo.
- Con un "Ya transferí" sin confirmar, el botón pasa a "Transferencia en revisión".
- El monto a transferir es precio + IVA, sin comisión de Flow.
- Un gym con pausa manual que paga queda registrado como pagado, pero sigue en pausa hasta que el super admin lo despause.

Plan cerrado, listo para implementar. Orden sugerido: 0 → 1 → activación + 2 → 3 → 4 → 5 → 6 → 7 → 8.
