-- V16: Telegram Mini App, Xavfsizlik va Outbox jadvallari

-- 1. Clientlar uchun password_hash nullable (Telegram orqali parolsiz ro'yxatdan o'tish)
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;

-- 2. Telegram akkauntlari (Mijozning Telegram profili va tizimdagi user_id bog'lanishi)
CREATE TABLE IF NOT EXISTS telegram_accounts (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    phone VARCHAR(20),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    username VARCHAR(100),
    language_code VARCHAR(10),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_tg_accounts_user_id ON telegram_accounts(user_id);
CREATE INDEX IF NOT EXISTS idx_tg_accounts_phone ON telegram_accounts(phone);

-- 3. Telegram orqali ulashilgan kontakt (telefon) tasdiqlari
CREATE TABLE IF NOT EXISTS telegram_phone_verifications (
    id BIGSERIAL PRIMARY KEY,
    telegram_id BIGINT NOT NULL,
    phone VARCHAR(20) NOT NULL,
    verified_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_tg_phone_verification UNIQUE (telegram_id, phone)
);

CREATE INDEX IF NOT EXISTS idx_tg_phone_verif_tg_id ON telegram_phone_verifications(telegram_id);
CREATE INDEX IF NOT EXISTS idx_tg_phone_verif_phone ON telegram_phone_verifications(phone);

-- 4. Xabarnomalar qutisi (Notification Outbox - bot orqali buyurtma xabarlarini ishonchli yuborish)
CREATE TABLE IF NOT EXISTS notification_outbox (
    id BIGSERIAL PRIMARY KEY,
    channel VARCHAR(30) NOT NULL, -- TELEGRAM, PUSH, SMS
    target VARCHAR(100) NOT NULL, -- chatId or phone or fcmToken
    title VARCHAR(255),
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, SENT, FAILED
    retry_count INT NOT NULL DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_notif_outbox_status ON notification_outbox(status);
CREATE INDEX IF NOT EXISTS idx_notif_outbox_created ON notification_outbox(created_at);
