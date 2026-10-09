-- Auditoría de performance 2026-10-09 (skill sql-optimization-patterns).

-- reservation.cancelled_by_closure_id (V31) nunca tuvo índice — ClosureNotificationService la
-- consulta (findByCancelledByClosureId) y hacía seq scan completo de la tabla que más crece del
-- sistema. Parcial porque el 99%+ de las filas tiene este valor en NULL (reservas normales nunca
-- lo tocan) — no agrega costo de escritura al camino caliente de reservar/cancelar.
CREATE INDEX idx_reservation_cancelled_by_closure_id
    ON reservation (cancelled_by_closure_id)
    WHERE cancelled_by_closure_id IS NOT NULL;

-- payment.flow_token (V15) nunca tuvo índice — es la clave con la que llega CADA webhook de
-- confirmación de pago de Flow.cl (FlowPaymentService.handleWebhook), el único punto de entrada
-- externo real del sistema de pagos. Único porque cada intento de pago genera un token distinto;
-- parcial porque los pagos manuales/transferencia no tienen flow_token.
CREATE UNIQUE INDEX uk_payment_flow_token
    ON payment (flow_token)
    WHERE flow_token IS NOT NULL;

-- app_user.role — AppUserRepository.findByRole (vista "Administradores" del super-admin, lee
-- todos los SUPER_ADMIN/GYM_ADMIN de TODOS los gimnasios) hacía seq scan de la tabla cross-tenant
-- que más crece junto con reservation.
CREATE INDEX idx_app_user_role ON app_user (role);

-- Los 2 índices simples de V2 quedaron subsumidos por los compuestos de V28
-- (idx_reservation_block_date, idx_reservation_member_status) — cualquier query que filtre solo
-- por gym_block_id o solo por member_id ya usa el prefijo del índice compuesto. Mantenerlos solo
-- suma costo de escritura en la tabla de mayor volumen de INSERT/UPDATE del sistema (reservar,
-- cancelar, check-in), sin aportar cobertura que los compuestos no den ya.
DROP INDEX idx_reservation_gym_block_id;
DROP INDEX idx_reservation_member_id;
