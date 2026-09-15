-- URL del panel del super-admin (/admin/gyms/:id) usaba el id secuencial de
-- la tabla en crudo — cualquiera podía ver cuántos gimnasios existen
-- incrementando el número, y el nombre del cliente no debía quedar expuesto
-- en una URL compartida/capturada en pantalla. gen_random_uuid() es nativo
-- desde Postgres 13, no requiere extensión.
ALTER TABLE gym ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE gym ADD CONSTRAINT uk_gym_public_id UNIQUE (public_id);
