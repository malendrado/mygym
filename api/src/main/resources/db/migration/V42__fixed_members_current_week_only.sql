-- Socios con clases fijas: solo reservan y cancelan en la semana en curso (lunes a domingo) — interruptor en
-- Horarios → Reglas de reserva (2026-10-12). Apagado por defecto: los gyms actuales no notan ningún cambio.
ALTER TABLE gym ADD COLUMN fixed_members_current_week_only BOOLEAN NOT NULL DEFAULT false;
