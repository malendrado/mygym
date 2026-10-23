-- Cambio de plan programado de un gimnasio que ya paga: el plan a medida (Escala) no trae cupo, así que el cupo nuevo se guarda
-- junto al plan y al precio pendientes y los tres rigen a la vez en el corte. NULL = el cupo lo da el plan pendiente.
-- ADD COLUMN sin valor por defecto no reescribe la tabla; el bloqueo dura un instante, así que mejor fallar rápido.
SET LOCAL lock_timeout = '5s';

ALTER TABLE gym_subscription ADD COLUMN IF NOT EXISTS pending_max_users_override INTEGER;
ALTER TABLE gym_subscription DROP CONSTRAINT IF EXISTS ck_gym_subscription_pending_max_users;
ALTER TABLE gym_subscription ADD CONSTRAINT ck_gym_subscription_pending_max_users CHECK (pending_max_users_override IS NULL OR pending_max_users_override >= 1) NOT VALID;
ALTER TABLE gym_subscription VALIDATE CONSTRAINT ck_gym_subscription_pending_max_users;
