-- The role the app connects as in the integration tests. The container user owns the tables and runs Flyway.
CREATE ROLE im_app LOGIN PASSWORD 'test-only';
