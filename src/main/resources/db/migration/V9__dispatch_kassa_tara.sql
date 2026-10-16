-- V9__dispatch_kassa_tara.sql
-- Backend TZ: Dispatch, Kassa va Tara (Oct 9, 2026)

-- 1. PRODUCTS: is_returnable, tara_price
ALTER TABLE products ADD COLUMN IF NOT EXISTS is_returnable BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE products ADD COLUMN IF NOT EXISTS tara_price NUMERIC(14, 2) NULL;

-- Mavjud qaytariladigan mahsulotlar uchun migratsiya
UPDATE products 
SET is_returnable = true, 
    tara_price = CASE WHEN deposit_price > 0 THEN deposit_price ELSE 40000.00 END
WHERE deposit_price > 0 OR volume_liters >= 18.0;

-- 2. ORDER_ITEMS: volume_liters_snapshot, tara_price_snapshot
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS volume_liters_snapshot NUMERIC(6, 2) NULL;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS tara_price_snapshot NUMERIC(14, 2) NULL;

-- 3. ORDERS: delivery_sequence, anchor_order_id, preparing_since, preparing_reason, dispatch_attempts, products_sum, tara_qty_estimated, tara_qty_final, tara_sum
ALTER TABLE orders ADD COLUMN IF NOT EXISTS delivery_sequence INT DEFAULT 1;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS anchor_order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS preparing_since TIMESTAMP WITH TIME ZONE NULL;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS preparing_reason VARCHAR(50) NULL; -- NO_STOCK, NO_FREE_COURIER, ALL_DECLINED
ALTER TABLE orders ADD COLUMN IF NOT EXISTS dispatch_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS products_sum NUMERIC(14, 2) NULL;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tara_qty_estimated INT DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tara_qty_final INT NULL;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS tara_sum NUMERIC(14, 2) DEFAULT 0;

-- Mavjud buyurtmalar uchun products_sum = total_sum
UPDATE orders SET products_sum = total_sum WHERE products_sum IS NULL;

-- 4. COURIER_OFFERS: distance_basis, anchor_order_id, eta_seconds
ALTER TABLE courier_offers ADD COLUMN IF NOT EXISTS distance_basis VARCHAR(30) DEFAULT 'CURRENT_LOCATION';
ALTER TABLE courier_offers ADD COLUMN IF NOT EXISTS anchor_order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL;
ALTER TABLE courier_offers ADD COLUMN IF NOT EXISTS eta_seconds INT NULL;

-- 5. FARM_DISPATCH_SETTINGS
CREATE TABLE IF NOT EXISTS farm_dispatch_settings (
    farm_id BIGINT PRIMARY KEY REFERENCES farms(id) ON DELETE CASCADE,
    max_active_orders INT NOT NULL DEFAULT 2,
    second_order_radius_m INT NOT NULL DEFAULT 1500,
    service_time_min INT NOT NULL DEFAULT 5,
    preparing_alert_min INT NOT NULL DEFAULT 15,
    updated_by BIGINT REFERENCES users(id),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

INSERT INTO farm_dispatch_settings (farm_id, max_active_orders, second_order_radius_m, service_time_min, preparing_alert_min)
SELECT id, 2, 1500, 5, 15 FROM farms
ON CONFLICT (farm_id) DO NOTHING;

-- 6. CASH_HANDOVERS
CREATE TABLE IF NOT EXISTS cash_handovers (
    id BIGSERIAL PRIMARY KEY,
    courier_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    farm_id BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    declared_amount NUMERIC(14, 2) NOT NULL CHECK (declared_amount > 0),
    confirmed_amount NUMERIC(14, 2) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, CONFIRMED, REJECTED
    initiated_by VARCHAR(30) NOT NULL DEFAULT 'COURIER', -- COURIER, MANAGER
    manager_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    note TEXT,
    reject_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    resolved_at TIMESTAMP WITH TIME ZONE NULL
);
CREATE INDEX IF NOT EXISTS idx_cash_handovers_courier ON cash_handovers(courier_id, status);
CREATE INDEX IF NOT EXISTS idx_cash_handovers_farm ON cash_handovers(farm_id, status);

-- 7. CASH_LEDGER
CREATE TABLE IF NOT EXISTS cash_ledger (
    id BIGSERIAL PRIMARY KEY,
    courier_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    farm_id BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL, -- COLLECTED, HANDOVER
    amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    handover_id BIGINT REFERENCES cash_handovers(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_cash_ledger_courier ON cash_ledger(courier_id);
CREATE INDEX IF NOT EXISTS idx_cash_ledger_farm ON cash_ledger(farm_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_cash_ledger_order_type ON cash_ledger(order_id, type) WHERE order_id IS NOT NULL;

-- 8. CLIENT_BOTTLE_BALANCES
CREATE TABLE IF NOT EXISTS client_bottle_balances (
    client_id BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    farm_id BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    balance INT NOT NULL DEFAULT 0 CHECK (balance >= 0),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (client_id, farm_id, product_id)
);
CREATE INDEX IF NOT EXISTS idx_client_bottle_client ON client_bottle_balances(client_id);

-- 9. BOTTLE_LEDGER
CREATE TABLE IF NOT EXISTS bottle_ledger (
    id BIGSERIAL PRIMARY KEY,
    client_id BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    farm_id BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    delta INT NOT NULL,
    reason VARCHAR(40) NOT NULL, -- DELIVERED_FULL, EMPTY_RETURNED, MANUAL_ADJUST
    order_id BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    actor_id BIGINT REFERENCES users(id),
    note TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_bottle_ledger_client ON bottle_ledger(client_id, farm_id);
CREATE INDEX IF NOT EXISTS idx_bottle_ledger_order ON bottle_ledger(order_id);
