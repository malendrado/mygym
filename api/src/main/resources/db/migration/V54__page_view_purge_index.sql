-- Soporta la purga diaria de visitas viejas (ExpiredRowsPurgeJob): el DELETE filtra solo por created_at, y los
-- índices de V27 arrancan por (page, ...) así que no sirven para un rango de fechas sin page. Sin este índice cada purga
-- recorrería toda la tabla.
-- Postgres corre cada migración en una transacción: CREATE INDEX CONCURRENTLY no se puede acá, así que se limita la
-- espera por el lock (page_view solo recibe INSERTs sueltos y la tabla aún es chica) en vez de quedarse bloqueando.
SET LOCAL lock_timeout = '5s';

CREATE INDEX IF NOT EXISTS idx_page_view_created_at ON page_view (created_at);
