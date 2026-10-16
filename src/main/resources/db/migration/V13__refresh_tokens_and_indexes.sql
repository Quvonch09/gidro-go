-- ============================================================================
-- V13 (BE-07 / BUG-018, CHK-10; BE-09; BE-01)
-- ============================================================================

-- Refresh tokenlar bazada: logout va parol almashtirishda bekor qilinadi, /auth/refresh da aylantiriladi.
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    VARCHAR(255) NOT NULL,
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked       BOOLEAN NOT NULL DEFAULT false
);
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now();
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMP WITH TIME ZONE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_rt_user ON refresh_tokens(user_id);

-- Buyurtmalar ro'yxati va statistika uchun indekslar
CREATE INDEX IF NOT EXISTS idx_orders_status_created ON orders(status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_farm_created ON orders(farm_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_completed ON orders(farm_id, completed_at) WHERE status = 'COMPLETED';
CREATE INDEX IF NOT EXISTS idx_orders_created ON orders(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_osh_order_changed ON order_status_history(order_id, changed_at);
CREATE INDEX IF NOT EXISTS idx_finance_farm_type_created ON finance_transactions(farm_id, type, created_at);
CREATE INDEX IF NOT EXISTS idx_users_role_status ON users(role, status);
CREATE INDEX IF NOT EXISTS idx_users_created ON users(created_at);
