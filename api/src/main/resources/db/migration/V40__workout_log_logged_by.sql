-- Quién anotó el registro de Memoria Viva: null = lo anotó el propio socio; un id = un profesor o admin lo
-- anotó por él durante la clase. Sin FK: es solo registro (si borran esa cuenta, el historial no cambia).
ALTER TABLE member_workout_log ADD COLUMN logged_by_user_id BIGINT;
