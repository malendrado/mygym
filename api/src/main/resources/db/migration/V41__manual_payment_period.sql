-- Activar un plan con fecha de inicio real y clases ya usadas (2026-10-12): el pago manual guarda desde cuándo vale el
-- período que activó y cuántas clases el socio ya había usado fuera de la app. paid_at sigue siendo el momento en que el
-- admin lo registró (auditoría); estas dos columnas son para los informes. Nulas en los pagos anteriores.
ALTER TABLE manual_payment ADD COLUMN period_start DATE;
ALTER TABLE manual_payment ADD COLUMN used_classes_outside INT;
