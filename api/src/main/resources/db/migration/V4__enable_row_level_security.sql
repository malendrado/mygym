-- Supabase exposes every public-schema table via its auto-generated REST API
-- unless Row-Level Security is enabled. This app never uses that API (the
-- backend talks to Postgres directly over JDBC as the table-owning role,
-- which bypasses RLS) — so enabling RLS with no policies fully locks the
-- REST API out while leaving the app itself unaffected.
-- flyway_schema_history is deliberately excluded: altering it from inside a
-- Flyway-managed migration deadlocks against Flyway's own locking queries on
-- that same table (confirmed 2026-09-08 — the migration hung indefinitely).
-- It holds only migration version/checksum metadata, not app data, so it's
-- low-risk to leave as-is.
ALTER TABLE gym ENABLE ROW LEVEL SECURITY;
ALTER TABLE gym_block ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_user ENABLE ROW LEVEL SECURITY;
ALTER TABLE reservation ENABLE ROW LEVEL SECURITY;
