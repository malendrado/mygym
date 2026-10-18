-- Renovación anticipada: pagar con el plan vigente ya no pisa el período (el socio perdía los días que le quedaban).
-- app_user.paid_at sigue siendo el INSTANTE REAL del último pago (informes, correos, CSV de desvinculación).
-- Los períodos salen de dos columnas nuevas, siempre nulas en los socios que ya existen (= comportamiento anterior):
--   period_start  inicio (ancla) de la cadena de períodos pagados seguidos; NULL = igual a paid_at.
--   paid_periods  cuántos meses calendario seguidos cubre la cadena; NULL = 1.
-- Índice parcial: solo indexa a quien renovó por adelantado (period_start no nulo), que son pocos; lo usa MembershipReminderJob
-- (findByRoleAndPeriodStartIsNotNull). Vacío al crearse, así que el CREATE INDEX no bloquea nada de forma apreciable.
-- El período k de la cadena es [ancla + (k-1) meses, ancla + k meses): cada borde se calcula SIEMPRE desde el ancla, así
-- 31 ene -> 28 feb -> 31 mar no se corre un día por mes.
-- ADD COLUMN sin valor por defecto no reescribe la tabla; solo toma el bloqueo un instante, así que si hubiera una
-- transacción larga abierta sobre app_user es mejor fallar rápido que encolar las consultas de la app.
SET LOCAL lock_timeout = '5s';

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS period_start TIMESTAMPTZ;
ALTER TABLE app_user ADD COLUMN IF NOT EXISTS paid_periods INTEGER;
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS ck_app_user_paid_periods;
-- NOT VALID + VALIDATE: el CHECK se agrega sin escanear la tabla bajo bloqueo exclusivo y luego se valida con un bloqueo más liviano.
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_paid_periods CHECK (paid_periods IS NULL OR paid_periods >= 1) NOT VALID;
ALTER TABLE app_user VALIDATE CONSTRAINT ck_app_user_paid_periods;

CREATE INDEX IF NOT EXISTS idx_app_user_period_start ON app_user (role) WHERE period_start IS NOT NULL;
