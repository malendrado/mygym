-- Seteado en CADA login real con Google (AuthService.updateGoogleProfile), a diferencia de
-- updated_at que solo se toca cuando algo cambia (primera vez, o si cambió la foto de Google) —
-- por eso updated_at no sirve como "última vez que entró". Se usa hoy solo para mostrar esto en
-- la grilla de accesos a la demo del panel de super-admin, pero aplica a cualquier AppUser.
ALTER TABLE app_user ADD COLUMN last_login_at TIMESTAMP;
