-- Auditoría 2026-10-17 (skill supabase-postgres-best-practices, "schema-foreign-key-indexes"): claves foráneas sin índice.
-- Postgres no las indexa solas, y sin índice cada borrado en la tabla referenciada (un día de rutina, un plan, un usuario)
-- recorre entera la tabla hija para ver si tiene filas que cascadear o bloquear. Parciales donde la columna es casi siempre NULL.

-- member_workout_log crece ~1 fila por clase asistida y socio; plan_day_id solo se llena si el socio eligió otro día.
CREATE INDEX IF NOT EXISTS idx_member_workout_log_plan_day
    ON member_workout_log (plan_day_id) WHERE plan_day_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_manual_payment_plan ON manual_payment (plan_id);
CREATE INDEX IF NOT EXISTS idx_manual_payment_registered_by
    ON manual_payment (registered_by_user_id) WHERE registered_by_user_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_plan ON payment (plan_id) WHERE plan_id IS NOT NULL;

-- Borrar un plan de membresía revisa si algún socio lo tiene contratado.
CREATE INDEX IF NOT EXISTS idx_app_user_plan ON app_user (plan_id) WHERE plan_id IS NOT NULL;
