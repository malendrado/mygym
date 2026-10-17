-- Auditoría 2026-10-17: V31 creó tres claves foráneas sin ON DELETE, así que:
--   * "Desvincular gimnasio" (gymRepository.delete) fallaba con violación de FK apenas el gimnasio hubiera tenido un
--     cierre de emergencia (gym_closure.gym_id → gym), y los datos del cliente nunca se borraban;
--   * borrar un bloque que aparece en un cierre parcial (gym_closure_block.gym_block_id → gym_block) fallaba con 500.
-- Ahora el cierre muere con su gimnasio, el vínculo cierre-bloque muere con el bloque, y una reserva que quedó
-- cancelada por un cierre que ya no existe conserva su historial (SET NULL).
--
-- Los nombres de las FK de V31 los puso Postgres solos: en vez de adivinarlos, se buscan por tabla de origen y de
-- destino, así que esto funciona igual en cualquier base donde V31 ya corrió.
DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT conrelid::regclass::text AS table_name, conname
        FROM pg_constraint
        WHERE contype = 'f'
          AND ((conrelid = 'gym_closure'::regclass AND confrelid = 'gym'::regclass)
            OR (conrelid = 'gym_closure_block'::regclass AND confrelid = 'gym_block'::regclass)
            OR (conrelid = 'reservation'::regclass AND confrelid = 'gym_closure'::regclass))
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.table_name, fk.conname);
    END LOOP;
END $$;

-- NOT VALID + VALIDATE: separa el ALTER (rápido) del escaneo de validación. OJO: Flyway corre toda la migración en UNA
-- transacción, así que los bloqueos del ALTER se mantienen hasta el final y la validación no corre "aparte": el costo
-- real es el escaneo de reservation con sus escrituras detenidas unos segundos, aceptable con el volumen actual. Si
-- reservation crece mucho, mover los VALIDATE a una migración propia. Las filas existentes ya cumplían la FK
-- anterior, que apuntaba a las mismas tablas.
ALTER TABLE gym_closure
    ADD CONSTRAINT fk_gym_closure_gym FOREIGN KEY (gym_id) REFERENCES gym (id) ON DELETE CASCADE NOT VALID;
ALTER TABLE gym_closure VALIDATE CONSTRAINT fk_gym_closure_gym;

ALTER TABLE gym_closure_block
    ADD CONSTRAINT fk_gym_closure_block_gym_block FOREIGN KEY (gym_block_id) REFERENCES gym_block (id) ON DELETE CASCADE NOT VALID;
ALTER TABLE gym_closure_block VALIDATE CONSTRAINT fk_gym_closure_block_gym_block;

ALTER TABLE reservation
    ADD CONSTRAINT fk_reservation_cancelled_by_closure FOREIGN KEY (cancelled_by_closure_id)
        REFERENCES gym_closure (id) ON DELETE SET NULL NOT VALID;
ALTER TABLE reservation VALIDATE CONSTRAINT fk_reservation_cancelled_by_closure;

-- Índices en las columnas que ahora cascadean: sin ellos, cada cierre o bloque que se borra obliga a recorrer entera
-- la tabla hija para encontrar sus filas (reservation y gym_closure_block). gym_closure.gym_id ya lo cubre
-- idx_gym_closure_gym_dates (gym_id es su primera columna). El de reservation es parcial: casi todas son NULL.
CREATE INDEX idx_reservation_cancelled_by_closure
    ON reservation (cancelled_by_closure_id) WHERE cancelled_by_closure_id IS NOT NULL;
CREATE INDEX idx_gym_closure_block_gym_block ON gym_closure_block (gym_block_id);
