-- Datos de mygym (la empresa) configurables desde el panel de super admin: contacto, datos de la empresa para el contrato y cuenta
-- bancaria para transferencias. UNA sola fila (id = 1). NULL o vacío = se usa el valor de respaldo del servidor (variables de entorno
-- o los datos históricos), así que crear la tabla no cambia nada hasta que el super admin guarde el formulario.
CREATE TABLE mygym_settings (
    id SMALLINT PRIMARY KEY,
    -- Contacto que ven los gimnasios (pop-up de pausa, contrato, correos). WhatsApp solo dígitos con código de país.
    whatsapp VARCHAR(20),
    contact_email VARCHAR(160),
    -- Empresa que contrata (aparece en el contrato de servicios).
    company_name VARCHAR(160),
    company_rut VARCHAR(20),
    legal_rep_name VARCHAR(120),
    legal_rep_rut VARCHAR(20),
    company_address VARCHAR(240),
    -- Cuenta a la que transfieren los gimnasios.
    bank_holder VARCHAR(160),
    bank_rut VARCHAR(20),
    bank_name VARCHAR(80),
    bank_account_type VARCHAR(40),
    bank_account_number VARCHAR(40),
    bank_email VARCHAR(160),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by BIGINT,
    CONSTRAINT ck_mygym_settings_single_row CHECK (id = 1)
);

INSERT INTO mygym_settings (id) VALUES (1);

-- Solo la API (rol de la conexión) la toca; sin políticas, el acceso directo por la API de Supabase queda cerrado.
ALTER TABLE mygym_settings ENABLE ROW LEVEL SECURITY;
