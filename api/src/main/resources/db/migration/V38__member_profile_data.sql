-- Datos de contacto obligatorios del socio (2026-10-12): RUT, dirección, fecha de nacimiento y
-- celular. Nullable en la base porque los socios que ya existen no los tienen: la app se los pide
-- al entrar (pantalla obligatoria) y el servidor no deja reservar hasta que estén completos
-- (ver AppUser.isProfileComplete / ReservationService.book). El RUT queda guardado formateado
-- ("12.345.678-5") y el celular en formato internacional ("+56912345678", sirve para wa.me).
ALTER TABLE app_user ADD COLUMN rut VARCHAR(12);
ALTER TABLE app_user ADD COLUMN address VARCHAR(200);
ALTER TABLE app_user ADD COLUMN birth_date DATE;
ALTER TABLE app_user ADD COLUMN phone VARCHAR(20);
