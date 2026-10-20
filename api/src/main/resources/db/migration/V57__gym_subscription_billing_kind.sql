-- A quien emite mygym la factura: una empresa (RUT de empresa, razon social) o una persona natural con inicio de actividades
-- (RUT y nombre de la persona). Los datos son los mismos cinco; el tipo solo cambia los titulos del formulario y de los correos.
-- Los gimnasios que ya completaron la factura la completaron como empresa, que es el valor por defecto.
-- Columna con valor por defecto constante: Postgres no reescribe la tabla (cambio de solo catalogo).
ALTER TABLE gym_subscription
    ADD COLUMN billing_kind VARCHAR(10) NOT NULL DEFAULT 'COMPANY',
    ADD CONSTRAINT ck_gym_subscription_billing_kind CHECK (billing_kind IN ('COMPANY', 'PERSON'));
