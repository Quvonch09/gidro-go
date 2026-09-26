-- ============================================================================
-- GidroGo v2.0 Database Schema - PostgreSQL 15/18 (Shared Schema with farm_id)
-- ============================================================================

-- 1. Farms (Fermalar)
CREATE TABLE IF NOT EXISTS farms (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    phone         VARCHAR(20) NOT NULL,
    address       VARCHAR(500),
    latitude      NUMERIC(10, 7),
    longitude     NUMERIC(10, 7),
    logo_url      VARCHAR(500),
    status        VARCHAR(20) NOT NULL DEFAULT 'INACTIVE', -- ACTIVE, BLOCKED, INACTIVE
    boss_user_id  BIGINT,                                 -- 1 ta ferma = 1 ta boss
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_farms_status ON farms(status);

-- 2. Users (Yagona foydalanuvchilar jadvali)
CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    farm_id       BIGINT REFERENCES farms(id) ON DELETE SET NULL, -- SuperAdmin / Mijoz uchun NULL
    role          VARCHAR(20) NOT NULL,                           -- SUPER_ADMIN, BOSS, MANAGER, COURIER, CLIENT
    full_name     VARCHAR(255) NOT NULL,
    username      VARCHAR(100) UNIQUE,                            -- SuperAdmin uchun
    phone         VARCHAR(20) UNIQUE,                             -- Boss, Manager, Courier, Client
    email         VARCHAR(255) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status        VARCHAR(25) NOT NULL DEFAULT 'ACTIVE',          -- ACTIVE, BLOCKED, PENDING_APPROVAL
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_users_farm ON users(farm_id);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);

-- Add foreign key back to farms for boss_user_id
ALTER TABLE farms ADD CONSTRAINT fk_farms_boss FOREIGN KEY (boss_user_id) REFERENCES users(id) ON DELETE SET NULL;

-- 3. Boss Activation Requests (Boss faollashtirish so'rovlari)
CREATE TABLE IF NOT EXISTS boss_activation_requests (
    id            BIGSERIAL PRIMARY KEY,
    farm_id       BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    boss_user_id  BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
    reject_reason VARCHAR(500),
    requested_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    decided_at    TIMESTAMP WITH TIME ZONE,
    decided_by    BIGINT REFERENCES users(id)             -- SuperAdmin id
);
CREATE INDEX IF NOT EXISTS idx_bar_status ON boss_activation_requests(status);
CREATE INDEX IF NOT EXISTS idx_bar_farm ON boss_activation_requests(farm_id);

-- 4. Service Regions (Manager tomonidan belgilanadigan xizmat hududlari)
CREATE TABLE IF NOT EXISTS service_regions (
    id            BIGSERIAL PRIMARY KEY,
    farm_id       BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    region_name   VARCHAR(100) NOT NULL, -- Masalan: 'Qarshi', 'Shahrisabz'
    polygon_json  TEXT NOT NULL,         -- [[lat, lon], [lat, lon], ...] GeoJSON ko'rinishida
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_regions_farm ON service_regions(farm_id);

-- 5. Products (Mahsulotlar - faqat Manager boshqaradi)
CREATE TABLE IF NOT EXISTS products (
    id            BIGSERIAL PRIMARY KEY,
    farm_id       BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    name          VARCHAR(100) NOT NULL,  -- '19L suv', '5L suv'
    price         NUMERIC(12,2) NOT NULL, -- Yetkazib berish ichida
    is_active     BOOLEAN NOT NULL DEFAULT true,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_products_farm ON products(farm_id);

-- 6. Warehouse Stock & Vehicle Stock (Ombor va Mashina Zaxirasi)
CREATE TABLE IF NOT EXISTS warehouse_stock (
    id            BIGSERIAL PRIMARY KEY,
    farm_id       BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity      NUMERIC(12,2) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(farm_id, product_id)
);

CREATE TABLE IF NOT EXISTS vehicle_stock (
    id            BIGSERIAL PRIMARY KEY,
    courier_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity      NUMERIC(12,2) NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE(courier_id, product_id)
);

CREATE TABLE IF NOT EXISTS restock_log (
    id            BIGSERIAL PRIMARY KEY,
    courier_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity      NUMERIC(12,2) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_restock_courier ON restock_log(courier_id);

-- 7. Clients & Client Addresses
CREATE TABLE IF NOT EXISTS clients (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    rating_avg    NUMERIC(3,2) DEFAULT 5.00,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS client_addresses (
    id            BIGSERIAL PRIMARY KEY,
    client_id     BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    label         VARCHAR(100), -- 'Uy', 'Ish'
    address       VARCHAR(500) NOT NULL,
    latitude      NUMERIC(10, 7) NOT NULL,
    longitude     NUMERIC(10, 7) NOT NULL,
    is_default    BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS idx_addr_client ON client_addresses(client_id);

-- 8. Orders & Order Items
CREATE TABLE IF NOT EXISTS orders (
    id               BIGSERIAL PRIMARY KEY,
    order_number     VARCHAR(32) NOT NULL UNIQUE,
    cart_group_id    UUID,                                                   -- Ko'p-fermali savatni bog'lash
    farm_id          BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    client_id        BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    courier_id       BIGINT REFERENCES users(id) ON DELETE SET NULL,
    status           VARCHAR(25) NOT NULL DEFAULT 'NEW',
                     -- NEW, SEARCHING, ASSIGNED, ON_THE_WAY, NEARBY, DELIVERED, COMPLETED, PREPARING, CANCELLED, PROBLEM, RETURNED
    payment_method   VARCHAR(20) NOT NULL,                                   -- CASH, ONLINE
    payment_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING',                 -- PENDING, PAID, CASH_COLLECTED, FAILED
    total_sum        NUMERIC(12,2) NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    latitude         NUMERIC(10, 7) NOT NULL,
    longitude        NUMERIC(10, 7) NOT NULL,
    client_comment   VARCHAR(500),
    assigned_at      TIMESTAMP WITH TIME ZONE,
    delivered_at     TIMESTAMP WITH TIME ZONE,
    completed_at     TIMESTAMP WITH TIME ZONE,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_orders_farm ON orders(farm_id);
CREATE INDEX IF NOT EXISTS idx_orders_client ON orders(client_id);
CREATE INDEX IF NOT EXISTS idx_orders_courier ON orders(courier_id);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_cart_group ON orders(cart_group_id);

CREATE TABLE IF NOT EXISTS order_items (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id    BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity      NUMERIC(10,2) NOT NULL,
    unit_price    NUMERIC(12,2) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_order_items_order ON order_items(order_id);

-- 9. Order Logs, Photos & Problems
CREATE TABLE IF NOT EXISTS order_status_history (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    from_status   VARCHAR(25),
    to_status     VARCHAR(25) NOT NULL,
    changed_by    BIGINT REFERENCES users(id) ON DELETE SET NULL, -- NULL = tizim avtomatik
    changed_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_osh_order ON order_status_history(order_id);

CREATE TABLE IF NOT EXISTS order_problem_log (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    reason_code   VARCHAR(40) NOT NULL, -- CLIENT_UNREACHABLE, ADDRESS_NOT_FOUND, VEHICLE_ISSUE, PRODUCT_ISSUE, FUEL_EMPTY, OTHER
    reason_text   VARCHAR(500),
    reported_by   BIGINT NOT NULL REFERENCES users(id),
    reported_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS delivery_photos (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    photo_url     VARCHAR(500) NOT NULL,
    uploaded_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- 10. Finance Transactions (Ferma kesimida - global agregatsiya yo'q)
CREATE TABLE IF NOT EXISTS finance_transactions (
    id            BIGSERIAL PRIMARY KEY,
    farm_id       BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    type          VARCHAR(10) NOT NULL,     -- INCOME, EXPENSE
    category      VARCHAR(50) NOT NULL,     -- ORDER_PAYMENT, FUEL, SALARY, TRANSPORT, MAINTENANCE, OTHER
    amount        NUMERIC(14,2) NOT NULL,
    order_id      BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    courier_id    BIGINT REFERENCES users(id) ON DELETE SET NULL,
    note          VARCHAR(500),
    created_by    BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_finance_farm ON finance_transactions(farm_id);
CREATE INDEX IF NOT EXISTS idx_finance_type ON finance_transactions(type, category);
CREATE INDEX IF NOT EXISTS idx_finance_created ON finance_transactions(created_at);

-- 11. Ratings
CREATE TABLE IF NOT EXISTS ratings (
    id            BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    client_id     BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    target_type   VARCHAR(10) NOT NULL, -- COURIER, FARM
    target_id     BIGINT NOT NULL,
    stars         INTEGER NOT NULL CHECK (stars BETWEEN 1 AND 5),
    comment       VARCHAR(500),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_ratings_target ON ratings(target_type, target_id);

-- 12. SuperAdmin Audit Log (Faqat SuperAdmin harakatlari saqlanadi)
CREATE TABLE IF NOT EXISTS superadmin_audit_log (
    id            BIGSERIAL PRIMARY KEY,
    actor_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    action        VARCHAR(100) NOT NULL, -- FARM_CREATED, BOSS_APPROVED, BOSS_REJECTED, FARM_BLOCKED, etc.
    entity_type   VARCHAR(50),
    entity_id     BIGINT,
    metadata      TEXT,                  -- JSON string
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_sal_actor ON superadmin_audit_log(actor_id);

-- 13. Refresh Tokens & Telegram OTP Sessions
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    VARCHAR(255) NOT NULL,
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS idx_rt_user ON refresh_tokens(user_id);

CREATE TABLE IF NOT EXISTS telegram_otp_sessions (
    id            BIGSERIAL PRIMARY KEY,
    phone         VARCHAR(20) NOT NULL,
    code          VARCHAR(10) NOT NULL,
    chat_id       VARCHAR(50),
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    verified      BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_tos_phone ON telegram_otp_sessions(phone);

-- 14. Dastlabki SuperAdmin yaratish (username: admin, parol: Admin123!)
-- Parol BCrypt xesh: $2a$10$wEkgmF5UfZyP/v51V.8N1ecH0M8fOiqh3wYIvhxeqp.QvjN9Y9WvK
INSERT INTO users (role, full_name, username, password_hash, status)
VALUES ('SUPER_ADMIN', 'Tizim SuperAdmini', 'admin', '$2a$10$y1Gf.T0dNx5RPNlOvEtTe.jeMs7Rcd25UoO1B.OqrvevfjwQR.nLW', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;