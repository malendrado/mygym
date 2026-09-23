-- Bug real en prod: Google siempre devuelve el email en minúscula, pero un admin puede tipear
-- un email con mayúsculas al invitar a alguien. findByEmail/existsByEmail (código Java) hacían
-- match exacto sensible a mayúsculas, así que "Nombre@mail.com" (invitado a mano) y
-- "nombre@mail.com" (login real de Google) terminaban como DOS filas para la misma persona —
-- la invitada quedaba pegada en "pendiente" para siempre, la del login real era la cuenta
-- que realmente se usaba. El código Java ya normaliza a minúscula en cada lectura/escritura
-- (ver AppUser.normalizeEmail) — esto normaliza los datos que ya existían.
--
-- Defensivo a propósito: si dos filas YA colisionan al bajarlas a minúscula (mismo caso real
-- encontrado y resuelto a mano para un socio de Fortis, 2026-09-23), ninguna de las dos se toca
-- acá — quedan para revisión manual en vez de romper el deploy por un choque de índice único.
UPDATE app_user a
SET email = LOWER(TRIM(a.email))
WHERE LOWER(TRIM(a.email)) <> a.email
  AND NOT EXISTS (
    SELECT 1 FROM app_user b
    WHERE b.id <> a.id AND LOWER(TRIM(b.email)) = LOWER(TRIM(a.email))
  );
