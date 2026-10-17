-- Historial de rutina por ventanas de 7 días y "clase pendiente de anotar": ambas buscan, por socio, las clases CON
-- asistencia ordenadas por fecha descendente. Índice parcial: solo las reservas con check-in (una fracción de la
-- tabla), ya ordenado por fecha, así Postgres lee las más recientes y se detiene sin ordenar nada.
CREATE INDEX idx_reservation_member_checkedin_date
    ON reservation (member_id, class_date DESC)
    WHERE checked_in_at IS NOT NULL;
