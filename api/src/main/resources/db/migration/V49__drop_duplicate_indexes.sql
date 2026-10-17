-- Auditoría 2026-10-17: dos índices de V46/V47 repetían otros que ya existían (cada índice cuesta en cada escritura).
-- Se conservan los de V33; no cambia ninguna consulta porque las dos definiciones son idénticas.

-- DROP INDEX toma un bloqueo exclusivo un instante: si hubiera una transacción larga abierta sobre estas tablas, mejor
-- fallar rápido (el arranque se reintenta) que dejar las consultas de la app encoladas detrás.
SET LOCAL lock_timeout = '5s';

-- Mismo índice parcial que idx_reservation_cancelled_by_closure_id (V33).
DROP INDEX IF EXISTS idx_reservation_cancelled_by_closure;

-- uk_payment_flow_token (V33) ya es único y parcial sobre la misma columna y cubre la búsqueda del webhook de Flow.
DROP INDEX IF EXISTS idx_payment_flow_token;
