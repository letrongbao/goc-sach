-- Review and run manually on an existing goc_sach database before starting this code.
-- The application never executes this file automatically.
BEGIN;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS shop_name_snapshot VARCHAR(150);
UPDATE orders o SET shop_name_snapshot = s.name FROM shops s
WHERE o.shop_id = s.id AND o.shop_name_snapshot IS NULL;
ALTER TABLE orders ALTER COLUMN shop_name_snapshot SET NOT NULL;
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
  CHECK (status IN ('PENDING','CONFIRMED','SHIPPING','CANCELLED','COMPLETED'));
CREATE INDEX IF NOT EXISTS orders_shop_time ON orders(shop_id,created_at DESC,id DESC);

ALTER TABLE order_items ADD COLUMN IF NOT EXISTS title_snapshot VARCHAR(200);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS cover_url_snapshot VARCHAR(1000);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS condition_snapshot VARCHAR(20);
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS copy_code_snapshot VARCHAR(80);
UPDATE order_items i SET
  title_snapshot = p.title,
  cover_url_snapshot = COALESCE(p.image,''),
  condition_snapshot = c.book_condition,
  copy_code_snapshot = c.code
FROM products p, book_copies c
WHERE i.product_id = p.id AND i.copy_id = c.id
  AND (i.title_snapshot IS NULL OR i.cover_url_snapshot IS NULL
    OR i.condition_snapshot IS NULL OR i.copy_code_snapshot IS NULL);
ALTER TABLE order_items ALTER COLUMN title_snapshot SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN cover_url_snapshot SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN condition_snapshot SET NOT NULL;
ALTER TABLE order_items ALTER COLUMN copy_code_snapshot SET NOT NULL;

ALTER TABLE inventory_reservations ALTER COLUMN expires_at DROP NOT NULL;

COMMIT;
