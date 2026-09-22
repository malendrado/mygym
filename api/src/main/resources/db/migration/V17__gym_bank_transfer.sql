-- Datos bancarios del gym para que un socio le transfiera directo (opción de
-- pago alternativa a Flow) — el admin los carga en General, el socio los ve
-- de solo lectura al elegir un plan. Todo nullable: sin datos, esa opción de
-- pago queda oculta en /member (ver GymService.getBankTransferInfo).
ALTER TABLE gym ADD COLUMN bank_name VARCHAR(60);
ALTER TABLE gym ADD COLUMN bank_account_type VARCHAR(30);
ALTER TABLE gym ADD COLUMN bank_account_number VARCHAR(40);
ALTER TABLE gym ADD COLUMN bank_holder_rut VARCHAR(20);
ALTER TABLE gym ADD COLUMN bank_holder_name VARCHAR(120);
ALTER TABLE gym ADD COLUMN bank_confirmation_email VARCHAR(160);
