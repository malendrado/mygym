-- Paginación de "Mis reservas · Pasadas" (ReservationService.myPastReservations): filtra por
-- (member_id, status) y ordena/recorta por class_date. Con idx_reservation_member_status
-- (member_id, status) Postgres tendría que ordenar todas las reservas del socio en cada página;
-- con class_date como tercera columna el orden sale del propio índice. Es un superconjunto del
-- anterior (mismo prefijo), por eso se reemplaza en vez de mantener los dos.
CREATE INDEX idx_reservation_member_status_date ON reservation (member_id, status, class_date DESC);
DROP INDEX idx_reservation_member_status;
