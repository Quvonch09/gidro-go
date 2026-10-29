CREATE TABLE IF NOT EXISTS client_notifications (
    id            BIGSERIAL PRIMARY KEY,
    client_id     BIGINT NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    title         VARCHAR(255) NOT NULL,
    message       TEXT NOT NULL,
    type          VARCHAR(50) NOT NULL DEFAULT 'SYSTEM',
    reference_id  BIGINT,
    is_read       BOOLEAN NOT NULL DEFAULT false,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_client_notifications_client ON client_notifications(client_id);
CREATE INDEX IF NOT EXISTS idx_client_notifications_unread ON client_notifications(client_id, is_read);
CREATE INDEX IF NOT EXISTS idx_client_notifications_created ON client_notifications(created_at DESC);

