-- Última actividad real de un administrador de gimnasio. last_login_at solo cambia al iniciar sesión, pero la sesión dura ~30 días: un admin
-- que usa la app todos los días puede mostrar un inicio de sesión de hace semanas. last_seen_at se actualiza (como mucho una vez por hora)
-- cuando el admin hace cualquier acción en la app; así el super admin ve qué gimnasios de verdad la están usando.
-- ADD COLUMN sin valor por defecto no reescribe la tabla; el bloqueo dura un instante, así que mejor fallar rápido.
SET LOCAL lock_timeout = '5s';

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS last_seen_at TIMESTAMPTZ;
