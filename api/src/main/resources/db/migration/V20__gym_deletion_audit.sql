-- Registro permanente de desvinculaciones de gimnasios — deliberadamente SIN foreign key a gym
-- (el gimnasio ya no existe cuando se lee esto) y SIN datos personales de socios (solo conteos):
-- es la prueba de "qué se borró y cuándo", no un respaldo de quiénes eran los socios. Ver
-- GymDisconnectionService — nunca se actualiza ni se borra una fila de esta tabla.
CREATE TABLE gym_deletion_audit (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gym_name VARCHAR(255) NOT NULL,
    gym_slug VARCHAR(255) NOT NULL,
    admin_emails TEXT NOT NULL,
    member_count INT NOT NULL,
    reservation_count INT NOT NULL,
    payment_count INT NOT NULL,
    block_count INT NOT NULL,
    plan_count INT NOT NULL,
    photo_count INT NOT NULL,
    executed_by VARCHAR(255) NOT NULL,
    executed_at TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE gym_deletion_audit ENABLE ROW LEVEL SECURITY;

-- page_view.gym_id nunca tuvo ON DELETE CASCADE (a diferencia de app_user/gym_block/gym_plan/
-- gym_photo, todas arregladas para cascadear desde gym en V1/V2/V7/V8) — sin esto, borrar un
-- gimnasio con visitas registradas (LANDING/JOIN) fallaría con una violación de FK.
ALTER TABLE page_view DROP CONSTRAINT page_view_gym_id_fkey;
ALTER TABLE page_view ADD CONSTRAINT fk_page_view_gym FOREIGN KEY (gym_id) REFERENCES gym (id) ON DELETE CASCADE;

