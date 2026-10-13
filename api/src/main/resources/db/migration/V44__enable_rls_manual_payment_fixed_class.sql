-- V36 (manual_payment) y V37 (member_fixed_class) crearon tablas sin activar RLS: Supabase las exponia
-- por su API REST publica (alerta rls_disabled_in_public). El backend se conecta por JDBC como duenio de las
-- tablas, asi que RLS sin politicas bloquea la API REST sin afectar a la app (mismo criterio que V4).
ALTER TABLE manual_payment ENABLE ROW LEVEL SECURITY;
ALTER TABLE member_fixed_class ENABLE ROW LEVEL SECURITY;
