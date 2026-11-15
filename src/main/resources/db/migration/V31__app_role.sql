-- The app connects as its own role (IM_DB_USER), which may only read and write rows. Flyway migrates as the owner of
-- the tables (IM_DB_OWNER). Only the owner can switch a trigger off, so the connection of the app cannot change an
-- issued invoice or a document. The role exists before this migration runs, with its password: see the README.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${appuser}') THEN
        RAISE EXCEPTION 'role ${appuser} is missing: create it first, see the README';
    END IF;
END;
$$;

GRANT USAGE ON SCHEMA public TO ${appuser};
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ${appuser};
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO ${appuser};
-- The history of the migrations is the owner's. The export reads its version.
REVOKE INSERT, UPDATE, DELETE ON flyway_schema_history FROM ${appuser};
-- Tables of later migrations get the same rights.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO ${appuser};
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO ${appuser};
