-- Solo se usa para socios cargados desde el Excel de importación masiva con un plan de cupo
-- limitado (no "libre") que ya venían con clases usadas ese mes en el sistema anterior del gym.
-- Se resetea a NULL apenas el plan se renueva/paga de nuevo (simulatePlanPayment, Flow,
-- revokePlan) — solo vale para el período de la carga, nunca se acumula.
ALTER TABLE app_user ADD COLUMN used_sessions_at_import INTEGER;
