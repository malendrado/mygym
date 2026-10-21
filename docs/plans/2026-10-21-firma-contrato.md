# Plan: firma electrónica del contrato por el administrador del gimnasio

Fecha: 2026-10-21. Estado: **IMPLEMENTADO en local (2026-10-21), SIN commit ni deploy.**

## Lo implementado y los atajos tomados

- Backend: V58, `ContractService`, `GymAdminContractController` (+ `GET /api/gyms/{id}/subscription/contract/copy` del super admin), regla de intentos por usuario en `AuthRateLimitFilter`, `contract` dentro de `SubscriptionResponse`, y `requireSignedContract` en el pago con Flow y en "Ya transferí". Texto en `api/src/main/resources/contracts/contrato-v1.txt`.
- Front: `app-contract-sign` (dentro de la tarjeta de suscripción, después de los datos de factura) y estado del contrato en la ficha del super admin (con "Ver constancia" y aviso si registra un pago sin contrato firmado).
- **Atajos (shortcut):** (1) sin subida del certificado de vigencia de Escala: solo recordatorio; (2) los códigos usados o vencidos no se purgan (filas mínimas, con tope por hora); (3) el administrador recibe la constancia por correo, pero la pantalla del gimnasio no tiene botón "Ver constancia" (el endpoint existe: `GET /api/gym-admin/subscription/contract/copy`); (4) el trigger que impide editar o borrar la constancia no tiene prueba automática (necesita Postgres real): probarlo con `UPDATE` en una base de pruebas antes del primer cliente.
- **Cambios al texto del contrato respecto de lo planeado:** la cláusula 7 también conserva "la constancia de la firma de este contrato"; la política de privacidad lo dice y agrega IP y navegador entre los datos del administrador. Plazo de conservación de la constancia: pendiente del abogado.
- `contrato-final-mygym.docx` quedó actualizado (razón social, cláusula 2, cláusula 11 y cláusula 7); copia previa en el scratchpad de la sesión.
- Pruebas: `ContractServiceTest` (11) y `GymSubscriptionServiceTest` en verde; front 28 archivos / 225 pruebas; `ng build landing` sin errores. Fallos que YA existían: `GymAdminReadAccessTest` (falla igual en `HEAD` limpio) y las pruebas que levantan Spring sin `SUPABASE_DB_URL`.

## Objetivo

Que el administrador de un gimnasio firme el contrato de servicios dentro de mygym, con un código enviado a su correo, **antes de su primer pago** del plan que le precargó el super admin, y que quede un respaldo inmutable de esa aceptación.

## Decisiones cerradas

- **Cuándo:** antes del primer pago del gimnasio a mygym (Flow o "Ya transferí"). Mismo punto donde hoy se exigen los datos de factura (`requireBillingData` en `GymSubscriptionService`). Si el contrato cambia de versión, se pide firmar la nueva antes del siguiente pago.
- **Cómo:** firma electrónica simple (Ley 19.799) con código de 6 dígitos al **correo de la cuenta** del administrador que firma. Sin SMS ni WhatsApp.
- **Quién:** cualquier GYM_ADMIN del gimnasio. Declara nombre, RUT, **calidad en que firma** (titular persona natural / representante legal / apoderado), **ser mayor de edad** y **tener facultades** para obligar al gimnasio.
- **Escala:** a un gimnasio con plan Escala y factura como empresa, mygym le pide el **certificado de vigencia** por correo, fuera del sistema. La pantalla de firma lo avisa y la ficha del super admin lo recuerda. No hay subida de archivos.
- **Sin carnet ni documentos:** no se piden.
- **No afecta a los gimnasios exentos** (no pagan) ni el "Marcar pago" del super admin (que puede registrar un pago igual, con una advertencia si no hay firma).

## Texto del contrato

- Fuente: `docs/comercial/contrato-final-mygym.docx` (= resumido, con las decisiones ya tomadas). Se copia a `api/src/main/resources/contracts/contrato-v1.md` con marcadores (`{{gymName}}`, `{{gymRut}}`, `{{planName}}`, `{{netPrice}}`, `{{startDate}}`, `{{minTermEnd}}`, `{{signerName}}`, `{{signerRut}}`, `{{signerCapacity}}`, `{{signerEmail}}`, `{{date}}`). Versión = `v1`.
- Los valores entre corchetes que siguen en el texto quedan **con el número propuesto** salvo que se indique otro: pago a 10 días, suspensión a 15 días, 30 días para eliminar y pedir copia, 90 días respaldos, 48 h aviso de incidentes, 99% disponibilidad, 4 h hábiles / 2 días hábiles de soporte, domicilio Santiago, ciudad de firma Santiago.
- "Administrador principal" = quien firma (nombre y correo; se quita el celular del texto).
- **Proveedor (dato fijo del texto):** INFORMÁTICA LUIS CORTÉS ÁLVAREZ E.I.R.L., RUT 76.831.567-1, representada por Luis René Cortés Álvarez, RUT 16.302.718-6. La razón social del contrato final ("ASESORES Y CONSULTORES EN INFORMÁTICA…") está mal y se corrige.
- **Cláusula 2 reemplazada (aprobada 2026-10-21)** para que diga lo que hace el sistema:
  > **2. Precio y pago.** El Cliente paga por adelantado el precio mensual del plan, más IVA, en su día de corte (el día del mes en que se inició el servicio), por Flow.cl o por transferencia a la cuenta de mygym. Si paga con Flow, la comisión de Flow es de cargo del Cliente y se muestra antes de pagar. mygym emite la factura electrónica de cada pago y no cobra comisión por socio ni por transacción. El cambio de plan se pide a mygym y rige desde el siguiente día de corte. El precio puede reajustarse una vez cada 12 meses según el IPC; cualquier otro cambio se avisa con 30 días de anticipación y el Cliente puede terminar el contrato sin costo antes de que rija. Si el pago no se hace en el día de corte, el Cliente tiene 2 días de gracia; después mygym puede suspender el servicio, sin eliminar datos, hasta que se pague.
  - Al implementar, confirmar en `GymBilling` que la gracia sigue siendo de 2 días; si el código cambió, manda el código y se ajusta el texto.
- **Razón social en el sitio y documentos:** corregirla a INFORMÁTICA LUIS CORTÉS ÁLVAREZ E.I.R.L. en `privacidad.html`, `politica-privacidad.md`, `contrato-tipo-mygym.md/.docx`, `contrato-resumido-mygym.md` y `contrato-final-mygym.docx` (se despliega junto con la firma).
- **Cambio al texto (cláusula 11):** "El Cliente acepta este contrato con un código enviado al correo de su administrador, lo que constituye firma electrónica simple (Ley N° 19.799). Quien acepta declara ser mayor de edad y tener facultades para obligar al Cliente. Para el plan Escala, mygym podrá pedir un certificado de vigencia de la sociedad." Se actualiza también el .docx y el .md.
- **Política de privacidad (sección 2, administradores):** al firmar se guardan nombre, RUT, calidad en que firma, IP, navegador y fecha.

## Base de datos (V58)

1. `gym_contract_acceptance` (respaldo, una fila por firma):
   - `id`, `gym_id` (sin FK ni cascada: la constancia sobrevive a la desvinculación del gimnasio), `contract_version`, `template_sha256`, `rendered_text` (texto exacto mostrado), `rendered_sha256`.
   - Firmante: `signer_user_id`, `signer_email`, `signer_name`, `signer_rut`, `signer_capacity` (CHECK: `PERSON_OWNER`, `LEGAL_REP`, `ATTORNEY`), `adult_declared`, `powers_declared` (ambos CHECK = true).
   - Snapshot comercial: `gym_name`, `gym_rut`, `billing_kind`, `plan_code`, `net_price_clp`.
   - Evidencia: `code_sent_at`, `signed_at` (hora del servidor), `ip`, `user_agent` (recortado a 500).
   - Índice `(gym_id, contract_version)`.
   - **Inmutable:** trigger `BEFORE UPDATE OR DELETE` que lanza error.
2. `gym_contract_code` (códigos de un solo uso):
   - `id`, `gym_id`, `user_id`, `code_hash` (con el `PasswordEncoder` que ya se usa para contraseñas), `rendered_sha256` (el código solo sirve para ese texto exacto), `expires_at` (10 min), `attempts` (máx. 5), `used_at`, `created_at`.
   - Índice `(user_id, created_at)` para contar envíos.
3. `ENABLE ROW LEVEL SECURITY` en ambas (lo exige `MigrationRlsTest`).
4. Retención: la desvinculación del gimnasio NO borra `gym_contract_acceptance`; los códigos se pueden purgar a los 30 días en el cron existente. Plazo de conservación de la constancia: [5 años desde el término — confirmar con el abogado].

## Backend

- `ContractService`:
  - `current(gymId, user)`: estado (firmado o no, versión, fecha, firmante), texto renderizado y su huella. Si faltan datos de factura, responde "completa primero los datos de factura".
  - `sendCode(gymId, user)`: invalida códigos previos, genera 6 dígitos con `SecureRandom`, guarda el hash y envía el correo. Máximo 1 envío por minuto y 5 por hora por usuario.
  - `sign(gymId, user, request, ip, userAgent)`: bloquea la suscripción (`lock(gymId)` como en los pagos), revisa vigencia, intentos, uso y que la huella del texto no haya cambiado; compara el código; valida RUT con el validador de V57; guarda la aceptación y marca el código usado. **Los correos se envían después del commit** (lección de la auditoría 2: nada de correos dentro de la transacción).
  - `requireSignedContract(gymId)`: se llama junto a `requireBillingData` en `startFlowPayment` y `reportTransfer`.
- Endpoints bajo `/api/gym-admin/subscription/contract` (así el filtro de pausa ya los deja pasar a un gimnasio suspendido): `GET`, `POST /code`, `POST /sign`, `GET /copy` (constancia en HTML).
- Super admin: `SubscriptionResponse` suma `contractSignedAt`, `contractVersion`, `contractSigner`, `contractSignerCapacity`, `escalaCertificateNeeded`; `GET /api/admin/gyms/{gymId}/subscription/contract/copy`.
- `AuthRateLimitFilter`: regla por usuario para `/contract/code` y `/contract/sign` (10 por 15 min).
- IP: la misma lógica de `AuthRateLimitFilter.clientIp`, extraída si hace falta reutilizarla (solo registro, no seguridad).
- Correos (patrón de `MemberLifecycleEmailService`): (1) código; (2) constancia al firmante con copia a contacto@mygym.cl: contrato completo + bloque de firma (firmante, RUT, calidad, fecha y hora de Chile, IP, correo validado, versión, huella SHA-256).

## Frontend

- `gym-subscription` (panel del admin): orden **datos de factura → contrato → botones de pago**. Sin firma, los botones de pago quedan bloqueados con el texto "Firma el contrato para continuar".
- Modal `contract-sign`: texto del contrato con scroll (interpolación normal, sin `innerHTML`); campos nombre, RUT, calidad (selector); casillas "Leí y acepto el contrato" y "Declaro ser mayor de edad y tener facultades para obligar al gimnasio"; botón "Enviarme el código" (correo enmascarado); campo de 6 dígitos; "Firmar contrato"; aviso visible de éxito. Si es Escala y empresa: nota del certificado de vigencia.
- Ya firmado: "Contrato firmado el [fecha] por [nombre] · Descargar copia".
- Super admin (`gym-form`, sección suscripción): "Contrato: firmado el … por … (calidad) · Ver constancia" o "Sin firmar"; si es Escala: "Pedir certificado de vigencia". "Marcar pago" sin firma pide confirmación.
- Revisar en celular (400 px) y escritorio, en las dos pantallas (panel del gym y ficha del super admin son distintas).

## Pruebas

- `ContractServiceTest`: código vencido, 5 intentos y bloqueo, un solo uso, código de otro usuario, texto cambiado entre envío y firma, límites de envío, declaración falsa rechazada, RUT inválido.
- `GymSubscriptionService`: Flow y "Ya transferí" bloqueados sin firma y permitidos con firma; exentos sin cambios; versión nueva vuelve a exigir firma.
- Integración: el trigger impide UPDATE y DELETE en `gym_contract_acceptance`; `MigrationRlsTest` en verde.
- Spec del modal: botones deshabilitados hasta marcar casillas y completar campos.

## Despliegue

Paso 0: las 4 skills de auditoría sobre el diff. Luego tests, commit, push, `railway up --ci` (V58) y `deploy.sh` **juntos** (cambia el contrato de la API). Pasada ofensiva liviana: los endpoints nuevos dan 401 sin token y 403 con rol de profesor. Probar con sesión real: firmar con XaloGym y pagar.

## Pendiente del usuario antes de ejecutar

1. ~~Nombre y RUT de quien representa a mygym~~ → resuelto (Luis René Cortés Álvarez, 16.302.718-6).
2. ~~Valores de los corchetes~~ → se usan los propuestos.
3. Que el abogado vea la cláusula 2 nueva, el cambio de la cláusula 11 y el plazo de conservación (no bloquea la implementación, sí la publicación).

## Fuera de alcance

SMS/WhatsApp, firma avanzada con proveedor certificado, subida de documentos, PDF (la constancia es HTML; se puede imprimir a PDF desde el navegador).
