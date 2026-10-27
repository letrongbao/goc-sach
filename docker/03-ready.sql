-- Runs only on a new Compose volume, after schema and synthetic data.
BEGIN;
DO $$
DECLARE field record;
BEGIN
  IF current_database() <> 'goc_sach_final_demo' THEN
    RAISE EXCEPTION 'Dedicated demo database required';
  END IF;
  -- Preserve relative sample dates so voucher/return demonstrations stay usable.
  FOR field IN SELECT table_name, column_name FROM information_schema.columns
    WHERE table_schema = 'public' AND data_type IN ('timestamp with time zone', 'timestamp without time zone')
  LOOP
    EXECUTE format('UPDATE %I SET %I = %I + $1 WHERE %I IS NOT NULL',
      field.table_name, field.column_name, field.column_name, field.column_name)
      USING (CURRENT_TIMESTAMP - TIMESTAMPTZ '2026-10-26 10:23:39+00');
  END LOOP;
END $$;
CREATE TABLE compose_bootstrap (id integer PRIMARY KEY, ready boolean NOT NULL);
INSERT INTO compose_bootstrap VALUES (1, true);
COMMIT;
