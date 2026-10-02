-- V8__courier_dispatch_offers.sql
-- Real Dispatch: Kuryer takliflari va atomik race guard jadvali

CREATE TABLE IF NOT EXISTS courier_offers (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    courier_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, REJECTED, EXPIRED, CANCELLED
    distance_meters INT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at TIMESTAMP WITH TIME ZONE,
    reject_reason VARCHAR(500)
);

CREATE INDEX IF NOT EXISTS idx_courier_offers_courier_status ON courier_offers(courier_id, status);
CREATE INDEX IF NOT EXISTS idx_courier_offers_order_status ON courier_offers(order_id, status);
CREATE INDEX IF NOT EXISTS idx_courier_offers_expires_at ON courier_offers(expires_at);
