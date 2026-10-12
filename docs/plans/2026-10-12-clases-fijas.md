# Clases fijas por socio (carga por Excel + edición manual), 2026-10-12

Plan armado por Opus para que lo ejecute otra sesión (Sonnet). Las decisiones de abajo ya las cerró el usuario. **No volver a preguntarlas.**

## Pedido original

Un gimnasio quiere subir un Excel para dejar **precargada para siempre una clase diaria** a ciertos socios, en ciertos días. Ejemplo: Camila va los lunes y miércoles a las 19:00. Las reservas tienen que aparecer solas, descontarle la clase del plan y cargarle tantas clases como le queden en el plan.

## Decisiones cerradas por el usuario

1. **Renovación automática.** Cada vez que se registra un pago, sea por Flow o marcado a mano, se generan solas las reservas fijas del nuevo período. Eso es el "para siempre".
2. **Clase llena: sobrecupo.** La clase fija tiene prioridad. Se reserva aunque el bloque ya esté en su capacidad.
3. **Edición desde el panel además del Excel.** Cada socio tiene un botón "Clases fijas" en Socios para ver, agregar o quitar sus clases fijas. El Excel queda para cargas masivas.
4. **Aviso por email.** Va un solo correo resumen por cada carga que haya creado al menos una reserva. Ejemplo: "Te reservamos 8 clases: lunes y miércoles 19:00, del 13 al 31 de octubre".
5. **Los profesores (`PROFESOR`) también pueden** ver y editar las clases fijas de los socios y subir el Excel, igual que el gym-admin y el super-admin.
6. **El nombre de la clase es obligatorio en el Excel.** Un gym puede tener dos clases a la misma hora (por ejemplo GAP y CrossFit el lunes a las 19:00), así que el bloque se identifica por **día + hora + nombre de la clase**, nunca solo por día y hora.
7. **Todas las validaciones se hacen al subir el Excel, antes de confirmar.** Se muestra un resumen igual al del import de socios: filas ok, advertencias y errores, con el detalle por fila. Solo se puede confirmar cuando el admin ya vio ese resumen.

## Reglas de la sesión (obligatorias)

- **No desplegar** (ni Railway ni Vercel) hasta que el usuario lo pida explícitamente en ese turno.
- **No levantar la API local contra Supabase**, porque aplicaría la migración nueva en la base de producción. El backend se prueba con tests Mockito y el frontend con un servidor Node falso (patrón en la memoria `reference_mygym_e2e_testing_patterns_2026_10_09`; ojo: `/gym-admin` solo existe en `ng serve landing`, no en `app`).
- Commits **sin** línea `Co-Authored-By`, y solo si el usuario lo pide.
- Todo texto de UI y de emails en **español chileno** (tú, nunca voseo).
- Los archivos `logos/export/ig-post-*` no son de esta tarea. No agregarlos a ningún commit.
- Hay cambios sin commit en `app/src/app/pages/admin/gyms/closure-modal/*` (nuevo selector de clases del cierre). No tocarlos ni revertirlos.

## Contexto verificado en el código

- **Reserva:** la entidad `Reservation` tiene `gymBlockId`, `memberId`, `classDate`, `status` (BOOKED/CANCELLED), `checkedInAt` y `cancelledByClosureId`. El índice único parcial `uk_reservation_slot_booked (gym_block_id, member_id, class_date) WHERE status='BOOKED'` (V6) impide duplicar una reserva activa.
- **Cupo del plan:** el descuento ya es automático. `MemberService.remainingClasses(user)` cuenta las reservas **BOOKED con `classDate` dentro del período** `[paidAt, paidAt + 1 mes − 1 día]` más `usedSessionsAtImport`. Si devuelve `null`, el plan es libre o no hay plan. Crear una reserva BOOKED a futuro ya descuenta la clase, y cancelarla la devuelve. **No duplicar este cálculo.** Si hace falta el rango del período, extraer un método `currentPeriod(user)` en `MemberService` y que `remainingClasses` lo use.
- **Pagos:** `paidAt` se setea en dos lugares:
  - `FlowPaymentService` ~190, al confirmar un pago de Flow.
  - `GymService.simulatePlanPayment` ~782, en "Marcar pago", tanto del gym-admin como del super-admin.

  `GymService.revokePlan` ("Quitar plan") cancela las reservas BOOKED futuras. El import de socios por Excel (`MemberImportRowService`) también setea `paidAt`.
- **Reservar:** `ReservationService.book` valida, en este orden:
  - que el día coincida con el bloque;
  - la ventana de reserva;
  - que no haya cierre (`gymClosureService.closedReasonFor`);
  - que no esté duplicada;
  - la capacidad;
  - que tenga plan activo;
  - que le queden clases.

  Después llama a `notifyIfClassesExhausted` y a `waitlistService.clearOnBooked`.
- **Cancelar:** `ReservationService.cancel` (socio, respeta la ventana de cancelación) y `cancelAsAdmin`. Ambos llaman a `waitlistService.onSpotFreed`.
- **Excel de socios:** se parsea en el **frontend** con SheetJS (`xlsx` en `package.json`), en `app/src/app/pages/admin/gyms/import-members-modal/*`. Ese modal tiene plantilla descargable, vista previa con ok/advertencia/error y envío en bloques. El backend recibe filas ya parseadas (`POST .../members/import`, `MemberController` ~70) y procesa cada fila en `REQUIRES_NEW` (`MemberImportRowService`). **Copiar ese mismo patrón.**
- **Socios en el panel:** cada fila de `gym-admin.html` (~1019, `.member-actions`) tiene los botones Marcar pago / Quitar plan / Rutina / Eliminar. "Quitar plan" y "Eliminar" se ocultan para `PROFESOR` (`isProfesor()`). El super-admin tiene la misma lista en `gym-form`.
- **Aviso in-app de clases agotadas:** `member.ts` `showMembershipAlert` (popup "Usaste todas tus clases"). El email correspondiente es `MemberLifecycleEmailService.sendClassesExhaustedMember`.
- Última migración: `V36__booking_rules_and_manual_payment.sql`. La nueva es **V37**.

## Diseño

### Modelo de datos (V37)

Tabla nueva `member_fixed_class`:

| columna | tipo | nota |
|---|---|---|
| id | bigserial PK | |
| gym_id | bigint not null FK gym | para filtrar por gym y validar pertenencia |
| member_id | bigint not null FK app_user ON DELETE CASCADE | |
| gym_block_id | bigint not null FK gym_block ON DELETE CASCADE | si se borra el bloque, se va la clase fija |
| created_by_user_id | bigint null | quién la cargó (admin o super-admin) |
| created_at | timestamptz not null | |

- `UNIQUE (member_id, gym_block_id)`.
- **"Una clase diaria":** máximo una clase fija por socio por día de la semana. Se valida en el servicio, porque el día vive en `gym_block`. Si llega una segunda para el mismo día, la fila es un error: "Camila ya tiene clase fija el lunes (19:00)".
- Índice `(gym_id)`. `(member_id)` ya lo cubre el unique.

En `reservation` se agrega la columna `fixed_class_id bigint null`, con FK a `member_fixed_class` **ON DELETE SET NULL**. Indica que la reserva nació de una clase fija. Sirve para:
- mostrar la etiqueta "Fija" en la app del socio y en el panel;
- saber qué reservas futuras cancelar al quitar una clase fija;
- no volver a reservar una fecha que el socio canceló a propósito (ver idempotencia).

Revisar si el `ck_`/constraint de capacidad existe en la base. Hoy la capacidad se valida solo en código, así que el sobrecupo no rompe nada en la base.

### Servicio nuevo `FixedClassService`

Es el único que crea reservas fijas. Métodos:

1. `replaceForMember(gymId, memberId, Set<Long> blockIds, Long actorUserId)`: deja al socio exactamente con esas clases fijas.
   - Valida que cada bloque sea del gym y esté activo, y que haya uno por día como máximo.
   - Para las clases fijas que salen: cancela sus reservas **futuras que aún no empiezan** con `fixed_class_id` de esa clase. Las pasadas y las de hoy ya empezadas no se tocan. Avisa a la lista de espera con `onSpotFreed`.
   - Para las que entran: llama a `generateForCurrentPeriod`.
   - Devuelve un resumen: creadas, saltadas con motivo, canceladas.
2. `addForMember` / `removeForMember`: atajos de la ficha del socio que usan la misma lógica.
3. `generateForCurrentPeriod(member)`: el corazón del servicio. **Idempotente**:
   - Si el socio no tiene plan activo (`memberService.hasActiveMembership`), no hace nada. Las clases fijas quedan guardadas y se generan cuando pague.
   - Recorre las fechas desde **ahora** hasta el fin del período (`currentPeriod`). Para cada clase fija, toma cada fecha cuyo día de la semana coincide con el bloque y cuya hora de inicio todavía no pasó.
   - Ordena todas esas ocurrencias **cronológicamente**, mezclando todas las clases fijas del socio.
   - Por cada ocurrencia, en orden:
     - si ya tiene una reserva BOOKED en ese bloque y fecha, la salta (sea fija o normal);
     - si existe una reserva **CANCELLED** con `fixed_class_id` de esa clase y esa fecha, cancelada por el socio o el admin (`cancelledByClosureId == null`), la salta: el socio la soltó a propósito y no se le vuelve a imponer;
     - si hay cierre ese día (`closedReasonFor`), la salta con el motivo "Cierre: <motivo>";
     - si `remainingClasses` es distinto de null y ya es 0, **se detiene**: ya no le quedan clases;
     - si no cayó en ninguno de los casos anteriores, crea la reserva BOOKED con `fixed_class_id`, **sin validar capacidad** (sobrecupo, decisión 2) y sin ventana de reserva (la carga la hace el admin).
   - Si el plan es libre (`remainingClasses == null`), genera todas las ocurrencias hasta el fin del período.
   - Recalcular `remainingClasses` en cada vuelta cuesta una consulta por reserva. Mejor calcularlo una vez al inicio y descontar en memoria.
   - Llama a `waitlistService.clearOnBooked` por cada reserva creada. Igual que `book`, quita al socio de la lista de espera de esa clase si estaba anotado.
   - **No** llama a `notifyIfClassesExhausted`. En su lugar manda el email resumen (decisión 4), solo si creó al menos una reserva.
   - Devuelve `FixedClassGenerationResult(created, List<SkippedOccurrence>)`.

   Generar reservas no debe pasar por `ReservationService.book`, porque `book` valida ventana y capacidad. Extraer en `ReservationService` un método package-private o público `createBooked(block, member, date, fixedClassId)` que haga el save, y reusarlo desde `book` para no duplicar la creación.

4. **Gancho de renovación** (decisión 1): después de setear `paidAt` en `FlowPaymentService` (~190) y en `GymService.simulatePlanPayment` (~782), hay que llamar a `generateForCurrentPeriod(member)`.
   - Hacerlo **después del commit** y en modo best-effort. Recomendado: publicar un `MembershipPaidEvent(memberId)` y escucharlo con `@TransactionalEventListener(phase = AFTER_COMMIT)` en `FixedClassService`, con su propia transacción `REQUIRES_NEW`.
   - Si la generación falla, el pago **nunca** se revierte. Solo se registra en el log (`log.warn`).
   - El import de socios por Excel con plan **no** dispara generación, porque esos socios todavía no tienen clases fijas. No hace falta engancharlo.
5. **Quitar plan** (`revokePlan`): ya cancela todas las reservas BOOKED futuras. Las clases fijas **se mantienen** guardadas. Si vuelve a pagar, se regeneran solas. No cambiar `revokePlan`, solo verificar con un test que no rompe nada.
6. **Eliminar socio:** el `ON DELETE CASCADE` de `member_id` se encarga. Verificar que el borrado real de socios (`deleteMember`) borra antes las reservas o que no choca con la FK nueva de `reservation.fixed_class_id`. Con `SET NULL` no debería chocar.
7. **Desvincular gimnasio** (`GymDisconnectionService`): agregar `member_fixed_class` al borrado de datos del gym, en el orden correcto de FKs. Si el CSV de exportación lista reservas, incluir una columna "Fija (sí/no)".

### Endpoints

Los mismos en los dos paneles, siguiendo el patrón existente: gym-admin toma el gym del JWT y super-admin lo recibe por path. Los endpoints de gym-admin aceptan `GYM_ADMIN` **y `PROFESOR`** (decisión 5; revisar cómo se autoriza hoy al profesor en el endpoint de Rutina y copiar ese mismo criterio). El `DEMO_ADMIN` queda bloqueado como el resto de las escrituras.

| Método | gym-admin | super-admin | qué hace |
|---|---|---|---|
| GET | `/api/gym-admin/members/{memberId}/fixed-classes` | `/api/gyms/{gymId}/members/{memberId}/fixed-classes` | clases fijas del socio (bloque, día, hora, nombre) |
| PUT | ídem | ídem | body `{ blockIds: [...] }`: reemplaza el set completo y devuelve el resumen de generación |
| POST | `/api/gym-admin/fixed-classes/import/validate` | `/api/gyms/{gymId}/fixed-classes/import/validate` | **validación sin escribir nada** (dry-run): devuelve el resumen por fila para la vista previa |
| POST | `/api/gym-admin/fixed-classes/import` | `/api/gyms/{gymId}/fixed-classes/import` | carga masiva confirmada, ver abajo |

- Validar siempre que `memberId` y cada `blockId` pertenezcan al gym (`findByIdAndGymId`). Nunca revelar si existen en otro gym.
- El socio debe tener rol `MEMBER`.
- Import: tope de filas por request igual al del import de socios. Se procesa **por socio** en `REQUIRES_NEW`, para que un socio con error no tumbe a los demás.

### Excel de clases fijas

Plantilla `plantilla-clases-fijas-mygym.xlsx`, una fila por socio y día:

| Email | Día | Hora | Clase |
|---|---|---|---|
| camila@correo.cl | Lunes | 19:00 | GAP |
| camila@correo.cl | Miércoles | 19:00 | GAP |
| tomas@correo.cl | Lunes | 19:00 | CrossFit |

Las cuatro columnas son **obligatorias** (decisión 6). La plantilla trae una segunda hoja "Clases del gimnasio" con la lista exacta de clases activas (Día, Hora, Clase), generada al descargarla desde los bloques del gym. Así el admin copia el nombre tal cual en vez de adivinarlo.

- **Día:** acepta nombres con o sin tilde, en cualquier capitalización, y abreviaturas de 3 letras (lun, mar, mié/mie, jue, vie, sáb/sab, dom).
- **Hora:** acepta "19:00", "19.00", "7:00" y el número de hora que entrega Excel cuando la celda tiene formato de hora (fracción del día). Se normaliza a `HH:mm`.
- **Match del bloque:** se busca entre los bloques **activos** del gym por **día de la semana + hora de inicio + nombre de la clase** (`label`). Los tres tienen que coincidir. El nombre se compara sin tildes, sin mayúsculas y sin espacios de más ("crossfit" = "CrossFit"). Nunca se elige un bloque solo por día y hora, aunque haya uno solo a esa hora. Errores por fila, con el texto exacto:
  - falta el nombre: "Falta el nombre de la clase. Cópialo de la hoja 'Clases del gimnasio'";
  - no hay clase con ese nombre a esa hora: "No hay una clase 'GAP' el lunes a las 19:00. A esa hora hay: CrossFit". Listar lo que sí existe a esa hora ese día para que lo corrija rápido; si no hay nada a esa hora, "No hay clases el lunes a las 19:00";
  - dos bloques activos con el mismo día, hora y nombre (duplicado en los horarios del gym): "Hay 2 clases 'GAP' el lunes a las 19:00. Revisa tus horarios".
- **Socio:** se busca por email normalizado (`AppUser.normalizeEmail`) entre los socios del gym. Si no existe, la fila es un error: "No hay socio con ese email en tu gimnasio. Agrégalo primero en Socios".
- **Semántica:** el archivo **reemplaza** las clases fijas de cada socio que aparece en él (usa `replaceForMember`). Los socios que no aparecen no se tocan. Explicarlo en el modal: "Para cada socio del archivo, sus clases fijas quedan exactamente como en el archivo".
- **Validación completa al subir el archivo** (decisión 7), antes de confirmar nada. Flujo:
  1. el admin elige el archivo y el frontend lo lee con SheetJS;
  2. el frontend revisa el formato: encabezados, día y hora legibles, campos vacíos;
  3. manda las filas a `.../fixed-classes/import/validate`, que **no escribe nada**. Esto es necesario porque la lista de socios del panel está paginada y el frontend no puede saber si un email existe. El backend devuelve el estado de cada fila;
  4. se muestra el resumen con el mismo diseño del import de socios: contadores "X ok · Y advertencias · Z errores" y la tabla por fila;
  5. "Confirmar carga" solo procesa las filas ok y con advertencia. Si hay errores, se ofrece corregir el Excel y subirlo de nuevo, o seguir sin esas filas;
  6. si un socio tiene **alguna** fila en error, se excluyen **todas** sus filas, porque el archivo reemplaza su set completo y cargarlo a medias le dejaría clases fijas incompletas. Mostrarlo en el resumen: "Se omite a Camila: corrige sus filas con error".

  El backend repite las mismas validaciones en el import confirmado. Nunca confía en que la vista previa se hizo.

  Las validaciones de `validate` (por fila y por socio):
  - ok: "Camila Rojas · Lun y Mié 19:00".
  - advertencia: "Camila no tiene plan activo: se guardan sus clases fijas y se reservan cuando pague"; "El lunes 19:00 ya está lleno: se reservará igual con sobrecupo".
  - error: socio no existe en el gym, clase no encontrada (día + hora + nombre), dos clases el mismo día para un socio, fila repetida exacta, día u hora ilegibles, email mal formado.
- **Resultado** al terminar, por socio: "8 reservas creadas", "3 creadas · 2 saltadas (cierre el 18-10)", "Sin plan activo: guardadas, se reservan al pagar".

### Frontend

1. **Botón "Clases fijas" en Socios** (gym-admin y gym-form): un `ion-button` más en `.member-actions`, junto a "Rutina", con ícono `repeat-outline`. **Visible también para `PROFESOR`** (decisión 5), igual que "Rutina". El botón de importar clases fijas también se muestra al profesor. Si el socio tiene clases fijas, debajo del nombre se muestra una línea corta "Fija: Lun y Mié 19:00".
   - Para no hacer N llamadas, conviene que la lista de socios (`MemberResponse`) traiga `fixedClassesSummary: string | null`. El backend lo arma con **una sola consulta** por página: join de `member_fixed_class` con `gym_block` filtrado por los ids de la página. Nada de N+1, recordar la auditoría de performance del 2026-09-30.
2. **Modal `fixed-classes-modal`** (nuevo, compartido por los dos paneles, mismo patrón que `ClosureModal`/`MarkPaidModal`: emite, no llama a la API):
   - Reusar el **mismo selector agrupado por día** del nuevo `closure-modal`: filas con casilla, hora grande, nombre y profe debajo. Mostrar todos los días de la semana, sin filtro de rango.
   - Regla "una por día": al marcar una clase en un día que ya tenía otra, se reemplaza la anterior (comportamiento de radio por día), con un texto "Una clase fija por día".
   - Pie: "Se reservarán desde hoy hasta el 31-10 (fin de su plan), hasta completar sus clases". Si no tiene plan activo: "No tiene plan activo: se guardan y se reservan cuando pague".
   - Al guardar, toast de confirmación explícito con el resultado (memoria `feedback_success_confirmation_ux`): "Listo: 8 clases reservadas para Camila. Le enviamos un email".
3. **Import:** un segundo botón junto a "Importar socios" (gym-admin.html ~852) que abra un modal nuevo `import-fixed-classes-modal`. Copiar la estructura de `import-members-modal`: plantilla, lectura con SheetJS, vista previa, confirmación y resultado. No reusar el mismo modal con flags; son columnas y validaciones distintas.
4. **App del socio (`member.html`):**
   - En "Mis reservas" y en la grilla, una etiqueta discreta "Fija" en las reservas con `fixedClassId`. `ReservationResponse` y `GymBlockOccurrenceResponse` exponen `fixed: boolean`.
   - El socio **puede cancelar** una fecha puntual de su clase fija con la ventana de cancelación normal. Eso libera la clase del cupo y esa fecha no se vuelve a generar. Texto de confirmación: "Cancelas solo esta fecha; tu clase fija sigue para las próximas semanas".
   - **Popup de clases agotadas:** si el socio tiene 0 clases restantes pero tiene reservas fijas futuras, el popup "Usaste todas tus clases" confunde, porque no las usó: están reservadas. Cambiar a header "Tus clases ya están reservadas" y mensaje "Tus N clases de este período ya están reservadas como clases fijas. Si cancelas una a tiempo, esa clase vuelve a tu cupo". Se puede saber con el `fixed` de sus reservas futuras, que `/member` ya carga.
5. **Clase con sobrecupo:** donde se muestre `taken/capacity`, aceptar `taken > capacity` sin romper (ej. "11/10"):
   - en el panel, roster y lista de horarios;
   - en la app del socio, donde aparece como llena;
   - en la TV, donde la barra de progreso no puede pasar del 100% (usar `min(1, taken/capacity)`).

   Para los demás socios la clase sigue apareciendo llena. La lista de espera funciona igual.
6. **Tour/demo:** agregar un paso al tour del admin apuntando al botón "Clases fijas" del primer socio (`data-tour="members-fixed"`). Revisar `gym-admin.ts` (pasos del tour) y los datos demo.

### Email resumen

Agregarlo en `MemberLifecycleEmailService` como `sendFixedClassesScheduled(gym, member, summary)`, con el mismo layout que los demás emails del ciclo de vida:
- Asunto: "Te reservamos tus clases fijas en <Gym>".
- Cuerpo: días y horas ("Lunes y miércoles a las 19:00"), cantidad de reservas, rango de fechas y el link a la app. Si hubo fechas saltadas por cierre, nombrarlas.
- Recordatorio: "Si no puedes ir a una, cancélala desde la app hasta X antes para que esa clase vuelva a tu cupo".
- Best-effort: un error de envío nunca revierte las reservas.

## Casos borde (cubrir con tests)

| Caso | Comportamiento esperado |
|---|---|
| Plan de 8 clases, fijas Lun+Mié, quedan 10 fechas en el período | crea las 8 primeras cronológicamente y se detiene |
| Plan libre | crea todas las fechas hasta el fin del período |
| Ya tenía reservas normales que consumen cupo | solo crea hasta completar lo que queda |
| Ya tenía reserva BOOKED en esa clase y fecha | la salta, no duplica |
| El socio canceló una fecha fija y se vuelve a generar (re-subida, edición, renovación en el mismo período) | no la vuelve a crear |
| Fecha con cierre | la salta con el motivo; no consume cupo |
| Cierre creado **después** de generar | `GymClosureService` la cancela como a cualquier reserva (ya existe) y libera el cupo |
| Clase llena | reserva igual (sobrecupo) |
| Hoy, la clase ya empezó | no la crea |
| Socio sin plan activo | guarda las clases fijas, 0 reservas, sin email |
| Pago (Flow o manual) con clases fijas | genera las del nuevo período después del commit; un error no revierte el pago |
| Renovación anticipada (paga antes de que venza) | las reservas futuras ya creadas cuentan contra el nuevo período (así funciona `remainingClasses`); completa solo lo que falta, sin duplicar |
| Quitar una clase fija | cancela sus reservas futuras que no empezaron; avisa a la lista de espera; no toca las pasadas |
| Bloque desactivado | no se generan reservas en él (validar `isActive`); la clase fija queda pero se muestra como "Clase desactivada" en el modal |
| Bloque borrado | CASCADE borra la clase fija; las reservas quedan con `fixed_class_id = null` |
| Dos clases el mismo día para un socio | error en Excel y en el endpoint |
| GAP y CrossFit el lunes 19:00, fila dice "crossfit" | marca CrossFit (match sin mayúsculas/tildes) |
| GAP y CrossFit el lunes 19:00, fila sin nombre de clase | error "Falta el nombre de la clase"; nunca se adivina |
| Un solo bloque a esa hora pero el nombre no coincide | error, listando lo que hay a esa hora; no se marca el único disponible |
| Un socio con 2 filas ok y 1 con error | se excluyen sus 3 filas; los demás socios se cargan |
| `validate` llamado | no crea ni modifica nada (test que lo verifique) |
| PROFESOR edita o sube clases fijas | permitido en su gym; 404 para otro gym |
| Bloque o socio de otro gym | 404 sin filtrar existencia |
| DEMO_ADMIN | escritura bloqueada como el resto |

Tests Mockito sugeridos, separando clases si dos mocks comparten tipo (gotcha conocido de `@InjectMocks`):
- `FixedClassGenerationTest`: orden cronológico, tope por cupo, plan libre, saltos.
- `FixedClassReplaceTest`: reemplazo, cancelación de futuras, una por día.
- `FixedClassRenewalHookTest`: el listener after-commit; una falla no propaga.
- Un test del parser de Excel en el frontend (día, hora y fracción de Excel) si el proyecto ya tiene specs de utils. Si no, una función pura testeable.

## Verificación antes de dar por terminado

1. `mvn test` con `JAVA_HOME` de Eclipse Adoptium, con los tests nuevos y los existentes en verde. Los tests con contexto Spring fallan sin base de datos y eso ya pasaba antes.
2. `npx tsc --noEmit -p tsconfig.app.json` y `npx ng build landing` sin errores.
3. Prueba visual con el servidor Node falso en `ng serve landing`:
   - el modal de clases fijas (marcar, reemplazar en el mismo día, guardar);
   - el import con vista previa (filas ok, advertencia y error) y la plantilla descargada;
   - la etiqueta "Fija" y el popup nuevo en `/member`;
   - "11/10" en el panel y la barra de la TV topada.
4. Correr las skills de seguridad y performance completas (memoria `feedback_audit_skill_groups`) sobre el diff: IDOR en los endpoints nuevos, N+1 en la lista de socios y en la generación.
5. Informar al usuario qué se probó y qué no (por ejemplo, el gancho de Flow real).

## Fuera de alcance

- Más de una clase fija por día para un socio (el usuario pidió "una clase diaria").
- Clases fijas quincenales o con fecha de término propia (siempre son semanales e indefinidas hasta que se quiten).
- Que el socio cree o edite sus propias clases fijas desde la app (solo el admin).
