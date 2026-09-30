-- Soporta el GROUP BY nuevo de AnalyticsService.getSummary (antes agregaba en memoria Java
-- trayendo TODAS las filas de page_view con page='JOIN' sin límite — no escalaba con el
-- tiempo). Sin estos índices, el JOIN+GROUP BY por gym_id y los countBy* existentes caen en
-- un seq scan completo de la tabla a medida que crece.
CREATE INDEX idx_page_view_page_gym_id ON page_view (page, gym_id);
CREATE INDEX idx_page_view_page_created_at ON page_view (page, created_at);
