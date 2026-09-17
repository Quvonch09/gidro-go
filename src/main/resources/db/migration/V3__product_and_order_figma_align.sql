-- V3: Align database schema with Figma UI designs (Product details, Order slots & tara, Service regions toggle)

ALTER TABLE products ADD COLUMN IF NOT EXISTS volume_liters NUMERIC(6, 2) DEFAULT 19.0;
ALTER TABLE products ADD COLUMN IF NOT EXISTS deposit_price NUMERIC(12, 2) DEFAULT 0.0;
ALTER TABLE products ADD COLUMN IF NOT EXISTS image_url VARCHAR(500);
ALTER TABLE products ADD COLUMN IF NOT EXISTS description TEXT;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_slot VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS empty_bottles_returned INT DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS deposit_amount NUMERIC(12, 2) DEFAULT 0.0;

ALTER TABLE service_regions ADD COLUMN IF NOT EXISTS is_active BOOLEAN DEFAULT TRUE;
