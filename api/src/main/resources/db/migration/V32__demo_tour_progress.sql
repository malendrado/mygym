-- Progreso del tour guiado de la demo comercial (uno para el panel admin, otro para la vista de
-- socio) — a diferencia de page_view (anónimo, append-only, pensado para agregados), esto es
-- ESTADO por usuario: a qué paso llegó cada DEMO_ADMIN, para que el super-admin lo vea en la
-- tarjeta "Acceso a la demo". Un upsert manual en el service (no hay UPSERT nativo de Spring Data)
-- mantiene max_step como el mayor paso alcanzado, nunca retrocede si el prospecto vuelve atrás.
CREATE TABLE demo_tour_progress (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    demo_admin_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    tour VARCHAR(10) NOT NULL,
    max_step INTEGER NOT NULL,
    reached_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uk_demo_tour_progress UNIQUE (demo_admin_id, tour)
);

ALTER TABLE demo_tour_progress ENABLE ROW LEVEL SECURITY;
