-- Cuenta Pago Online (Flow.cl) propia de cada gym — reemplaza la cuenta única global: cada
-- gimnasio pasa a tener su propio comercio en Flow, así los pagos le llegan directo a su cuenta
-- bancaria (bank_* de V17, reusados tal cual, no duplicados) en vez de pasar por mygym. Todo
-- nullable: un gym sin estos datos simplemente no ofrece "Pagar con Flow" (ver
-- GymService.getFlowAccount / PublicGymResponse.flowConfigured), solo transferencia bancaria.
ALTER TABLE gym
    ADD COLUMN flow_company_rut VARCHAR(20),
    ADD COLUMN flow_company_name VARCHAR(160),
    ADD COLUMN flow_business_activity VARCHAR(160),
    ADD COLUMN flow_company_address VARCHAR(200),
    ADD COLUMN flow_vat_condition VARCHAR(80),
    ADD COLUMN flow_legal_rep_name VARCHAR(120),
    ADD COLUMN flow_legal_rep_rut VARCHAR(20),
    ADD COLUMN flow_legal_rep_phone VARCHAR(30),
    ADD COLUMN flow_contact_email VARCHAR(160),
    ADD COLUMN flow_contact_name VARCHAR(120),
    ADD COLUMN flow_contact_phone VARCHAR(30),
    -- Cifradas en reposo por EncryptedStringConverter (AES-256-GCM, clave en APP_ENCRYPTION_KEY)
    -- antes de llegar a esta columna — nunca se guarda ni se loguea en texto plano.
    ADD COLUMN flow_api_key TEXT,
    ADD COLUMN flow_secret_key TEXT;
