-- Runs once, on first creation of the postgres volume.
-- Schema objects are owned by Flyway; this file only enables extensions.
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
