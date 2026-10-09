-- Auditoría de performance 2026-10-09 (continuación). ClassWaitlistRepository.findByClassDateGreaterThanEqual
-- (WaitlistService.escalateExpiredHeadStarts, cron cross-tenant) filtra solo por class_date —
-- idx_class_waitlist_occurrence(gym_block_id, class_date) no sirve de mucho acá porque
-- gym_block_id es la columna líder. Volumen bajo hoy (solo entra en waitlist quien topa un
-- bloque lleno), pero el índice es barato y cierra el gap documentado en la auditoría.
CREATE INDEX idx_class_waitlist_class_date ON class_waitlist (class_date);
