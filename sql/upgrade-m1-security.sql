-- Review and execute manually ONLY in the FINAL PROJECT database.
-- Not needed for a fresh database using the current schema.sql.
-- Never run against the coursework database bookstore_03.
BEGIN;
ALTER TABLE users ADD COLUMN blocked BOOLEAN NOT NULL DEFAULT FALSE;
COMMIT;
