-- Cambio de plan programado: si un socio con plan vigente paga OTRO plan, el pago queda en espera y el plan nuevo parte
-- cuando se le acaben las clases o llegue su vencimiento (lo que ocurra primero), sin perder los meses que ya traía
-- pagados por adelantado. Tres columnas nulas en todos los socios que ya existen (= sin cambio pendiente):
--   pending_plan_id       plan pagado que se activará; si ese plan se borra, el cambio se descarta (ON DELETE SET NULL).
--   pending_plan_periods  cuántos meses seguidos pagó de ese plan (pagar el mismo plan pendiente de nuevo suma uno).
-- ADD COLUMN sin valor por defecto no reescribe la tabla; el bloqueo dura un instante, así que mejor fallar rápido.
SET LOCAL lock_timeout = '5s';

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS pending_plan_id BIGINT;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS pending_plan_periods INTEGER;
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS fk_app_user_pending_plan;
-- NOT VALID + VALIDATE: la FK se agrega sin escanear app_user bajo bloqueo fuerte (todas las filas nuevas ya la cumplen: son NULL).
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_pending_plan FOREIGN KEY (pending_plan_id) REFERENCES gym_plan (id) ON DELETE SET NULL NOT VALID;
ALTER TABLE app_user VALIDATE CONSTRAINT fk_app_user_pending_plan;
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS ck_app_user_pending_plan_periods;
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_pending_plan_periods CHECK (pending_plan_periods IS NULL OR pending_plan_periods >= 1) NOT VALID;
ALTER TABLE app_user VALIDATE CONSTRAINT ck_app_user_pending_plan_periods;

-- Índice parcial: solo quien tiene un cambio pendiente (pocos); lo recorre PlanChangeJob y, al borrar un plan, la FK ON DELETE SET NULL.
CREATE INDEX IF NOT EXISTS idx_app_user_pending_plan ON app_user (pending_plan_id) WHERE pending_plan_id IS NOT NULL;
