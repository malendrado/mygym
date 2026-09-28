-- Marca de asistencia real a una clase reservada, distinta de la reserva en sí (BOOKED/CANCELLED
-- no dice si el socio realmente vino). checked_in_at nulo = no vino todavía (o no vino nunca, si
-- la clase ya pasó) — se calcula "no show" en el momento, no se guarda un estado aparte.
ALTER TABLE reservation ADD COLUMN checked_in_at TIMESTAMP;

-- QR rotativo que la TV muestra durante una clase en curso — el socio lo escanea con su celular
-- para confirmar que está físicamente ahí. Mismo patrón que tv_pairing_code (código corto,
-- efímero), pero acá SÍ nace con gym_id (lo resuelve la propia pantalla ya emparejada) y puede
-- canjearlo más de un socio dentro de su ventana de validez — no es de un solo uso.
CREATE TABLE tv_checkin_code (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gym_id BIGINT NOT NULL,
    code VARCHAR(8) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_tv_checkin_code_gym FOREIGN KEY (gym_id) REFERENCES gym (id) ON DELETE CASCADE
);

CREATE INDEX idx_tv_checkin_code_gym_id ON tv_checkin_code (gym_id);

ALTER TABLE tv_checkin_code ENABLE ROW LEVEL SECURITY;
