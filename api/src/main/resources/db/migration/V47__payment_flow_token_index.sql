-- El webhook público de Flow busca (y ahora bloquea, FOR UPDATE) el pago por flow_token en cada aviso, y esa columna
-- nunca tuvo índice: cada webhook recorría la tabla payment entera. Parcial porque el token se asigna recién al crear
-- el checkout en Flow (los intentos que fallaron antes quedan NULL).
CREATE INDEX idx_payment_flow_token ON payment (flow_token) WHERE flow_token IS NOT NULL;
