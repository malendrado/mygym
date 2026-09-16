-- Foto de perfil de Google (claim "picture" del ID token) — antes no se
-- leía en absoluto. Es una URL de Google (lh3.googleusercontent.com), no
-- la imagen en sí; se refresca en cada login por si el usuario cambia su
-- foto en Google.
ALTER TABLE app_user ADD COLUMN photo_url TEXT;
