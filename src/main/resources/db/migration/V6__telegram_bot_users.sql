-- V6: Telegram bot users table to link phone numbers with Telegram chat IDs
CREATE TABLE IF NOT EXISTS telegram_users (
    id            BIGSERIAL PRIMARY KEY,
    telegram_id   BIGINT NOT NULL UNIQUE,
    chat_id       VARCHAR(50) NOT NULL,
    phone         VARCHAR(20),
    first_name    VARCHAR(255),
    last_name     VARCHAR(255),
    username      VARCHAR(255),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tu_phone ON telegram_users(phone);
CREATE INDEX IF NOT EXISTS idx_tu_chat_id ON telegram_users(chat_id);
