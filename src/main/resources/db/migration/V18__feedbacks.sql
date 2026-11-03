-- V18: Mijoz va kuryer fikr-mulohazalari (feedback). Manager ko'radi va holatini belgilaydi.
CREATE TABLE IF NOT EXISTS feedbacks (
    id             BIGSERIAL PRIMARY KEY,
    farm_id        BIGINT NOT NULL REFERENCES farms(id) ON DELETE CASCADE,
    source         VARCHAR(20) NOT NULL,                      -- CLIENT, COURIER
    author_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    order_id       BIGINT REFERENCES orders(id) ON DELETE SET NULL,
    category       VARCHAR(30) NOT NULL DEFAULT 'OTHER',      -- SERVICE, DELIVERY, PRODUCT, COURIER, APP, PAYMENT, OTHER
    message        VARCHAR(1000) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'NEW',        -- NEW, SEEN, RESOLVED
    manager_note   VARCHAR(1000),
    handled_by     BIGINT REFERENCES users(id) ON DELETE SET NULL,
    handled_at     TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_feedbacks_farm_status ON feedbacks(farm_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_feedbacks_author ON feedbacks(author_user_id, created_at DESC);
