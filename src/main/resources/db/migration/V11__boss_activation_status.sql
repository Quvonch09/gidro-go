-- ============================================================================
-- V11 (BE-05, BE-16 / CHK-01, CHK-02): Boss faollashtirish holati
--
-- users.activation_status: NOT_REQUESTED -> PENDING -> APPROVED | REJECTED (faqat BOSS uchun).
-- Hisob holati (users.status: ACTIVE/BLOCKED/DELETED) alohida qoladi.
--
-- Q11 (loyiha egasi tavsiyasi): mavjud bosslar APPROVED deb belgilanadi, oqim faqat
-- yangi bosslarga qo'llanadi. Egasi boshqa qaror qilsa — bitta UPDATE bilan o'zgartiriladi.
-- ============================================================================

-- Xavfsizlik tekshiruvi: bitta bossda bir nechta PENDING so'rov bo'lsa — to'xtaymiz
DO $$
DECLARE
    problems TEXT := '';
    r RECORD;
BEGIN
    FOR r IN
        SELECT boss_user_id, string_agg(id::text, ',' ORDER BY id) AS ids
        FROM boss_activation_requests WHERE status = 'PENDING'
        GROUP BY boss_user_id HAVING COUNT(*) > 1
    LOOP
        problems := problems || format(E'\n  boss #%s: bir nechta PENDING so''rov [%s]', r.boss_user_id, r.ids);
    END LOOP;
    IF problems <> '' THEN
        RAISE EXCEPTION E'V11 to''xtatildi: takroriy PENDING so''rovlar bor, qaysi biri qolishini egasi hal qiladi:%', problems;
    END IF;
END $$;

ALTER TABLE users ADD COLUMN IF NOT EXISTS activation_status VARCHAR(20);

-- 1) Eski oqimdan qolgan PENDING_APPROVAL hisob holati: kutilayotgan so'rov bo'lsa PENDING,
--    oxirgi so'rov rad etilgan bo'lsa REJECTED, aks holda NOT_REQUESTED. Hisob holati ACTIVE ga qaytadi.
UPDATE users u SET
    activation_status = CASE
        WHEN EXISTS (SELECT 1 FROM boss_activation_requests r WHERE r.boss_user_id = u.id AND r.status = 'PENDING')
            THEN 'PENDING'
        WHEN (SELECT r.status FROM boss_activation_requests r WHERE r.boss_user_id = u.id
              ORDER BY r.requested_at DESC LIMIT 1) = 'REJECTED'
            THEN 'REJECTED'
        ELSE 'NOT_REQUESTED' END,
    status = 'ACTIVE'
WHERE u.role = 'BOSS' AND u.status = 'PENDING_APPROVAL';

-- 2) Qolgan mavjud bosslar — APPROVED (Q11)
UPDATE users SET activation_status = 'APPROVED'
WHERE role = 'BOSS' AND activation_status IS NULL;

ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_activation_status;
ALTER TABLE users ADD CONSTRAINT chk_users_activation_status
    CHECK (activation_status IS NULL OR activation_status IN ('NOT_REQUESTED', 'PENDING', 'APPROVED', 'REJECTED'));

-- Bitta boss uchun bir vaqtda faqat bitta PENDING so'rov
CREATE UNIQUE INDEX IF NOT EXISTS uq_bar_one_pending_per_boss
    ON boss_activation_requests(boss_user_id) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_bar_requested_at ON boss_activation_requests(requested_at DESC);
