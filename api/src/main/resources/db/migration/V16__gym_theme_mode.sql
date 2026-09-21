-- Modo de theme del gym — hoy el fondo/tarjeta siempre eran oscuros sin importar
-- el acento elegido (deriveSurfaceTint con luminosidad fija). Agrega la opción de
-- modo claro, limitada a 4 paletas curadas (ver GymPalette.LIGHT_ALL), no color libre.
ALTER TABLE gym ADD COLUMN theme_mode VARCHAR(10) NOT NULL DEFAULT 'DARK';
ALTER TABLE gym ADD CONSTRAINT ck_gym_theme_mode CHECK (theme_mode IN ('DARK', 'LIGHT'));
