-- V19: Manager va Boss bildirishnomalari (kuryer bildirishnomalarining nusxasi, foydalanuvchi va ferma bo'yicha)
CREATE TABLE IF NOT EXISTS staff_notifications (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    farm_id      BIGINT REFERENCES farms(id) ON DELETE CASCADE,
    title        VARCHAR(255) NOT NULL,
    message      TEXT NOT NULL,
    type         VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',   -- CASH_HANDOVER_REQUESTED, CASH_HANDOVER_CANCELLED, CASH_HANDOVER_SHORTAGE, FEEDBACK_CREATED, SYSTEM
    reference_id BIGINT,
    is_read      BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_staff_notifications_user ON staff_notifications(user_id, is_read, created_at DESC);
