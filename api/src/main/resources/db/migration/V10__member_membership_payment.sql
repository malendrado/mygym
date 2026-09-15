-- Hasta ahora el estado de pago de un socio (plan contratado, cuándo pagó)
-- vivía SOLO en el navegador del propio socio (member.ts) — se perdía al
-- recargar y ningún admin lo podía ver. Esto persiste lo mínimo necesario
-- para que el backend calcule Activo/Vencido/Sin pago de verdad. Sigue
-- siendo un registro manual (el admin marca "pagó"), no el pago real de
-- Flow.cl — eso queda para la Parte B ya planificada.
ALTER TABLE app_user ADD COLUMN plan_id BIGINT REFERENCES gym_plan (id);
ALTER TABLE app_user ADD COLUMN paid_at TIMESTAMP;
