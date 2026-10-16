-- ============================================================================
-- V12 (BE-12 / BUG-013, BUG-014, CHK-06): Audit Log kengaytmasi va o'zgarmaslik
-- ============================================================================

-- Audit jadvali (baseline qilingan bazalarda ham bo'lishi uchun IF NOT EXISTS)
CREATE TABLE IF NOT EXISTS superadmin_audit_log (
    id            BIGSERIAL PRIMARY KEY,
    actor_id      BIGINT NOT NULL REFERENCES users(id),
    action        VARCHAR(100) NOT NULL,
    entity_type   VARCHAR(50),
    entity_id     BIGINT,
    metadata      TEXT,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

ALTER TABLE superadmin_audit_log ADD COLUMN IF NOT EXISTS ip_address VARCHAR(64);
ALTER TABLE superadmin_audit_log ADD COLUMN IF NOT EXISTS user_agent VARCHAR(500);

-- Foydalanuvchi o'chirilganda audit yozuvlari kaskad bilan o'chib ketmasligi kerak
ALTER TABLE superadmin_audit_log DROP CONSTRAINT IF EXISTS superadmin_audit_log_actor_id_fkey;
ALTER TABLE superadmin_audit_log ADD CONSTRAINT superadmin_audit_log_actor_id_fkey
    FOREIGN KEY (actor_id) REFERENCES users(id) ON DELETE RESTRICT;

-- Eski PROFILE_UPDATE yozuvlarida entity_type/entity_id yo'q edi: ular SuperAdmin'ning o'ziga tegishli
-- (bu ma'lumot o'ylab topilmaydi — actor_id ning o'zi). O'zgarmaslik trigger'idan OLDIN bajariladi.
UPDATE superadmin_audit_log
SET entity_type = 'SUPERADMIN', entity_id = actor_id
WHERE action = 'PROFILE_UPDATE' AND entity_type IS NULL;

CREATE INDEX IF NOT EXISTS idx_sal_created_at ON superadmin_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sal_action ON superadmin_audit_log(action);
CREATE INDEX IF NOT EXISTS idx_sal_entity ON superadmin_audit_log(entity_type, entity_id);

-- O'zgarmaslik: Audit Log va ledgerlar faqat INSERT qilinadi. Tuzatish — faqat teskari yozuv bilan.
CREATE OR REPLACE FUNCTION gidrogo_forbid_modify() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% jadvalidagi yozuvlarni o''zgartirish yoki o''chirish taqiqlangan (faqat teskari yozuv qo''shiladi)', TG_TABLE_NAME
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_audit_log_immutable ON superadmin_audit_log;
CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE ON superadmin_audit_log
    FOR EACH ROW EXECUTE FUNCTION gidrogo_forbid_modify();

DROP TRIGGER IF EXISTS trg_cash_ledger_immutable ON cash_ledger;
CREATE TRIGGER trg_cash_ledger_immutable
    BEFORE UPDATE OR DELETE ON cash_ledger
    FOR EACH ROW EXECUTE FUNCTION gidrogo_forbid_modify();

DROP TRIGGER IF EXISTS trg_bottle_ledger_immutable ON bottle_ledger;
CREATE TRIGGER trg_bottle_ledger_immutable
    BEFORE UPDATE OR DELETE ON bottle_ledger
    FOR EACH ROW EXECUTE FUNCTION gidrogo_forbid_modify();

DROP TRIGGER IF EXISTS trg_order_status_history_immutable ON order_status_history;
CREATE TRIGGER trg_order_status_history_immutable
    BEFORE UPDATE OR DELETE ON order_status_history
    FOR EACH ROW EXECUTE FUNCTION gidrogo_forbid_modify();
