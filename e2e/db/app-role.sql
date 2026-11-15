-- The role the app connects as in the E2E stack. The database user "im" owns the tables and runs Flyway.
CREATE ROLE im_app LOGIN PASSWORD 'e2e-app-only';
