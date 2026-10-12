-- Asistencia: de qué forma quedó marcada ('QR' o 'MANUAL') y quién la marcó o desmarcó a mano.
-- checked_in_by_user_id no lleva FK: es solo registro de quién fue; si borran esa cuenta, el dato
-- histórico no debe frenar el borrado ni cambiar la reserva.
ALTER TABLE reservation ADD COLUMN checkin_method VARCHAR(10);
ALTER TABLE reservation ADD COLUMN checked_in_by_user_id BIGINT;
UPDATE reservation SET checkin_method = 'QR' WHERE checked_in_at IS NOT NULL;
