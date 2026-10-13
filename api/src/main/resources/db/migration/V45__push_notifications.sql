-- Notificaciones push (2026-10-13). Una fila por telefono/navegador suscrito: el backend la usa para enviarle avisos
-- (cupo liberado, recordatorio de clase, cierre, membresia; y cancelaciones al equipo del gimnasio).
CREATE TABLE push_subscription (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    endpoint        TEXT         NOT NULL,
    p256dh          VARCHAR(200) NOT NULL,
    auth            VARCHAR(100) NOT NULL,
    user_agent      VARCHAR(300),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_success_at TIMESTAMPTZ,
    failure_count   INT          NOT NULL DEFAULT 0,
    CONSTRAINT uk_push_subscription_endpoint UNIQUE (endpoint)
);
CREATE INDEX idx_push_subscription_user ON push_subscription (user_id);

-- OBLIGATORIO en toda tabla nueva: sin RLS, Supabase la expone por su API REST publica (alerta rls_disabled_in_public).
-- El backend entra por JDBC como duenio de la tabla, asi que RLS sin politicas no lo afecta (mismo criterio que V4).
ALTER TABLE push_subscription ENABLE ROW LEVEL SECURITY;

-- Recordatorio de clase ya enviado (o evaluado y descartado): evita repetirlo si el cron vuelve a correr o hay reinicio.
ALTER TABLE reservation ADD COLUMN reminder_pushed_at TIMESTAMPTZ;
-- Indice parcial para el cron de recordatorios: solo las reservas vigentes que aun no se evaluaron (conjunto chico).
CREATE INDEX idx_reservation_reminder_pending ON reservation (class_date) WHERE status = 'BOOKED' AND reminder_pushed_at IS NULL;
