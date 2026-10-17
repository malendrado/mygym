-- Auditoría 2026-10-17: el JWT dura 30 días y no se cortaba al cambiar la contraseña, así que un token robado seguía
-- sirviendo después de un reseteo. Los tokens emitidos ANTES de esta fecha dejan de valer (ver UserSessionGuard).
-- NULL = nunca se han invalidado sesiones: todos los tokens vigentes siguen valiendo. Sin índice: se lee por id.
ALTER TABLE app_user ADD COLUMN sessions_valid_after TIMESTAMPTZ;
