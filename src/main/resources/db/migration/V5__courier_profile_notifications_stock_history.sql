-- V5: Courier Profile, Notifications, and Stock History enhancements

-- 1. Users jadvaliga haydovchi va transport ma'lumotlari
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url VARCHAR(500);
ALTER TABLE users ADD COLUMN IF NOT EXISTS vehicle_model VARCHAR(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS vehicle_plate_number VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS max_capacity INT DEFAULT 40;
ALTER TABLE users ADD COLUMN IF NOT EXISTS driver_license_number VARCHAR(50);
ALTER TABLE users ADD COLUMN IF NOT EXISTS passport_serial VARCHAR(50);

-- 2. Restock log jadvaliga qo'shimcha ma'lumotlar (ombor joylashuvi va boshqaruvchi)
ALTER TABLE restock_log ADD COLUMN IF NOT EXISTS location VARCHAR(255) DEFAULT 'Markaziy baza';
ALTER TABLE restock_log ADD COLUMN IF NOT EXISTS warehouse_manager_name VARCHAR(255);

-- 3. Kuryer bildirishnomalari jadvali (In-App Notifications)
CREATE TABLE IF NOT EXISTS courier_notifications (
    id            BIGSERIAL PRIMARY KEY,
    courier_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    message       TEXT NOT NULL,
    type          VARCHAR(50) NOT NULL DEFAULT 'SYSTEM', -- ORDER_ASSIGNED, STOCK_RESTOCKED, BROADCAST, SYSTEM
    reference_id  BIGINT,
    is_read       BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_courier_notifications_courier ON courier_notifications(courier_id);
CREATE INDEX IF NOT EXISTS idx_courier_notifications_unread ON courier_notifications(courier_id, is_read);
CREATE INDEX IF NOT EXISTS idx_courier_notifications_created ON courier_notifications(created_at DESC);
