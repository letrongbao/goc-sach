-- REVIEW AND EXECUTE MANUALLY ONLY after backing up the existing goc_sach database.
-- Requires the current order schema (apply order-m3-manual.sql first if still on the old order version).
-- Application and tests never run this file. No rows or legacy columns are deleted.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

DO $$
BEGIN
  IF current_database() <> 'goc_sach' THEN
    RAISE EXCEPTION 'This upgrade is for the dedicated goc_sach database only';
  END IF;
  IF to_regclass('orders') IS NULL OR to_regclass('inventory_reservations') IS NULL THEN
    RAISE EXCEPTION 'The existing order schema is required before this upgrade';
  END IF;
END $$;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_method VARCHAR(20) NOT NULL DEFAULT 'COD';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_status VARCHAR(30) NOT NULL DEFAULT 'UNPAID';
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_expires_at TIMESTAMP WITH TIME ZONE;

-- Preserve previously recorded payment information; only backfill old COD defaults.
UPDATE orders SET payment_status='PAID'
 WHERE payment_method='COD' AND payment_status='UNPAID' AND status='COMPLETED';
UPDATE orders SET payment_status='CANCELLED'
 WHERE payment_method='COD' AND payment_status='UNPAID' AND status='CANCELLED';

DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='orders'::regclass AND conname='orders_payment_method_check') THEN
    ALTER TABLE orders ADD CONSTRAINT orders_payment_method_check CHECK(payment_method IN ('COD','PAYOS'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='orders'::regclass AND conname='orders_payment_status_check') THEN
    ALTER TABLE orders ADD CONSTRAINT orders_payment_status_check
      CHECK(payment_status IN ('UNPAID','PENDING','PAID','CANCELLED','EXPIRED','REVIEW_REQUIRED'));
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS orders_online_due ON orders(payment_expires_at,id)
 WHERE payment_method='PAYOS' AND status='PENDING' AND payment_status IN ('UNPAID','PENDING');

CREATE TABLE IF NOT EXISTS payment_attempts (
 id BIGSERIAL PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE REFERENCES orders(id) ON DELETE RESTRICT,
 order_code BIGINT NOT NULL UNIQUE CHECK(order_code > 0),
 amount BIGINT NOT NULL CHECK(amount > 0),
 currency VARCHAR(3) NOT NULL DEFAULT 'VND' CHECK(currency='VND'),
 status VARCHAR(30) NOT NULL DEFAULT 'CREATING'
   CHECK(status IN ('CREATING','PENDING','PROCESSING','PAID','CANCELLED','EXPIRED','REVIEW_REQUIRED')),
 payment_link_id VARCHAR(100) NOT NULL DEFAULT '',
 checkout_url VARCHAR(1000) NOT NULL DEFAULT '',
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS payment_attempts_open ON payment_attempts(updated_at,id)
 WHERE status IN ('CREATING','PENDING','PROCESSING');
CREATE TABLE IF NOT EXISTS payment_events (
 id BIGSERIAL PRIMARY KEY,
 attempt_id BIGINT NOT NULL REFERENCES payment_attempts(id) ON DELETE RESTRICT,
 amount BIGINT NOT NULL CHECK(amount >= 0),
 reference VARCHAR(200) NOT NULL UNIQUE,
 outcome VARCHAR(30) NOT NULL CHECK(outcome IN ('PAID','REVIEW_REQUIRED')),
 source VARCHAR(20) NOT NULL DEFAULT 'WEBHOOK' CHECK(source IN ('WEBHOOK','QUERY')),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS payment_events_attempt ON payment_events(attempt_id,created_at,id);
COMMIT;
