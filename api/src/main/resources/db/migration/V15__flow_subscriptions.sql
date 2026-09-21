-- Pago manual mes a mes con Flow.cl — el socio paga un mes por vez (sin
-- tarjeta guardada, sin cobro automático); "payment" es el historial/
-- auditoría de esos pagos, algo que no existía hasta ahora (solo quedaba
-- el último app_user.paid_at, sin trazabilidad de cuánto ni cuándo).
CREATE TABLE payment (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES app_user(id),
    plan_id BIGINT REFERENCES gym_plan(id),
    commerce_order VARCHAR(60),
    flow_token VARCHAR(120),
    flow_order VARCHAR(60),
    amount_clp INTEGER,
    status VARCHAR(20) NOT NULL,
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE payment ENABLE ROW LEVEL SECURITY;
