-- Pantallas de TV que muestran el horario en vivo del gym (bloque actual/siguiente, quién va).
-- Se vinculan por un código corto de emparejamiento (como Netflix/YouTube en una TV nueva) —
-- la TV nunca tipea una URL larga, solo un código de 6 caracteres que el admin ingresa desde su
-- panel. tv_pairing_code es efímero (expira solo, sin gym_id todavía — recién se sabe a qué gym
-- pertenece cuando un GYM_ADMIN autenticado lo reclama); tv_screen es la sesión real, de larga
-- duración, ya asociada al gym.
CREATE TABLE tv_pairing_code (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code VARCHAR(8) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    claimed_at TIMESTAMP,
    screen_token VARCHAR(64)
);

CREATE TABLE tv_screen (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    gym_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL,
    last_polled_at TIMESTAMP,
    CONSTRAINT fk_tv_screen_gym FOREIGN KEY (gym_id) REFERENCES gym (id) ON DELETE CASCADE
);

CREATE INDEX idx_tv_screen_gym_id ON tv_screen (gym_id);

ALTER TABLE tv_pairing_code ENABLE ROW LEVEL SECURITY;
ALTER TABLE tv_screen ENABLE ROW LEVEL SECURITY;
