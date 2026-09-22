-- Permite borrar un socio permanentemente (expulsión total, solo super-admin) sin que su
-- historial de pagos lo bloquee con una violación de FK. reservation.member_id ya tenía
-- ON DELETE CASCADE desde V2; a payment.member_id (creada en V15) le faltó ese mismo ON DELETE.
ALTER TABLE payment DROP CONSTRAINT payment_member_id_fkey;
ALTER TABLE payment ADD CONSTRAINT fk_payment_member FOREIGN KEY (member_id) REFERENCES app_user (id) ON DELETE CASCADE;
