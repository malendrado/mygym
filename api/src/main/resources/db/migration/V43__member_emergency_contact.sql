-- Contacto de emergencia obligatorio del socio (2026-10-12): nombre y celular. Nullable en la base porque los socios que
-- ya existen no lo tienen: la app se lo pide al entrar (pantalla obligatoria de datos) y el servidor no deja reservar
-- hasta que estén completos (ver AppUser.isProfileComplete). El celular queda en formato internacional ("+56912345678").
ALTER TABLE app_user ADD COLUMN emergency_contact_name VARCHAR(100);
ALTER TABLE app_user ADD COLUMN emergency_contact_phone VARCHAR(20);
