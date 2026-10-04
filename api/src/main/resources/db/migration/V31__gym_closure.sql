-- Cierre de emergencia de gimnasio: el admin del gym o el super-admin puede cerrar un rango de
-- fechas completo (whole_days) o clases puntuales (gym_closure_block) por fuerza mayor/seguridad.
-- Cancela reservas futuras, bloquea nuevas, devuelve cupo (gratis: MemberService cuenta reservas
-- BOOKED, cancelar ya libera el período) y notifica por email — ver GymClosureService.
CREATE TABLE gym_closure (
    id BIGSERIAL PRIMARY KEY,
    gym_id BIGINT NOT NULL REFERENCES gym(id),
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    whole_days BOOLEAN NOT NULL DEFAULT TRUE,
    reason TEXT NOT NULL,
    created_by_email VARCHAR(255) NOT NULL,
    created_by_role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    lifted_at TIMESTAMP,
    cancelled_reservations_count INTEGER NOT NULL DEFAULT 0,
    affected_members_count INTEGER NOT NULL DEFAULT 0,
    emails_sent INTEGER NOT NULL DEFAULT 0,
    emails_failed INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX idx_gym_closure_gym_dates ON gym_closure(gym_id, start_date, end_date);
ALTER TABLE gym_closure ENABLE ROW LEVEL SECURITY;

CREATE TABLE gym_closure_block (
    id BIGSERIAL PRIMARY KEY,
    closure_id BIGINT NOT NULL REFERENCES gym_closure(id) ON DELETE CASCADE,
    gym_block_id BIGINT NOT NULL REFERENCES gym_block(id),
    UNIQUE (closure_id, gym_block_id)
);
ALTER TABLE gym_closure_block ENABLE ROW LEVEL SECURITY;

ALTER TABLE reservation ADD COLUMN cancelled_by_closure_id BIGINT REFERENCES gym_closure(id);
