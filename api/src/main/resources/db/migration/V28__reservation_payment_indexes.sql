-- `reservation` solo tenía índices simples en gym_block_id y member_id por separado (V2), pese
-- a que las queries reales siempre filtran también por fecha/status:
--   findByGymBlockIdAndClassDateAndStatus / findByGymBlockIdInAndClassDateBetweenAndStatus
--   (booking en caliente, roster de una ocurrencia, batch de Historial/TV)
--   findByMemberIdAndStatus... (mis reservas del socio, gate de reserva, check-in QR)
-- Hoy el volumen es chico y un índice simple + filtro residual alcanza, pero a medida que
-- `reservation` crece sin archivado (ver comentario en ReservationService), el índice simple
-- sobre gym_block_id solo deja de alcanzar. `payment` (V15) no tenía ningún índice sobre
-- member_id (el FK no crea uno automático en Postgres).
CREATE INDEX idx_reservation_block_date ON reservation (gym_block_id, class_date);
CREATE INDEX idx_reservation_member_status ON reservation (member_id, status);
CREATE INDEX idx_payment_member_id ON payment (member_id);
