-- ============================================================================
-- V10 (BE-03 / BUG-016, CHK-04): Boss–ferma bog'lanishining yagona manbasi
--
-- Yagona manba: farms.boss_user_id (UNIQUE, NULL bo'lishi mumkin).
-- users.farm_id (BOSS uchun) — undan hosil qilinadigan nusxa; kod ikkalasini bitta
-- tranzaksiyada, ferma qatorini qulflab yangilaydi.
--
-- XAVFSIZ MIGRATSIYA: nomuvofiq juftlar bo'lsa, migratsiya TO'XTAYDI va ro'yxatni
-- chiqaradi. Hech qanday standart qiymat avtomatik qo'yilmaydi. Juftlarni loyiha egasi
-- bilan kelishib qo'lda tuzating (namuna: docs/sql/boss_farm_fix_template.sql), so'ng
-- ilovani qayta ishga tushiring.
-- ============================================================================

DO $$
DECLARE
    problems TEXT := '';
    r RECORD;
BEGIN
    -- 1) Ferma boshqa rol yoki o'chirilgan foydalanuvchini boss sifatida ko'rsatadi
    FOR r IN
        SELECT f.id AS farm_id, f.boss_user_id, u.role, u.status
        FROM farms f
        LEFT JOIN users u ON u.id = f.boss_user_id
        WHERE f.boss_user_id IS NOT NULL
          AND (u.id IS NULL OR u.role <> 'BOSS' OR u.status = 'DELETED')
    LOOP
        problems := problems || format(E'\n  farm #%s -> boss_user_id=%s (role=%s, status=%s): boss emas yoki o''chirilgan',
                                       r.farm_id, r.boss_user_id, COALESCE(r.role, 'YO''Q'), COALESCE(r.status, '-'));
    END LOOP;

    -- 2) Bitta foydalanuvchi bir nechta fermaga boss
    FOR r IN
        SELECT boss_user_id, string_agg(id::text, ',' ORDER BY id) AS farm_ids
        FROM farms WHERE boss_user_id IS NOT NULL
        GROUP BY boss_user_id HAVING COUNT(*) > 1
    LOOP
        problems := problems || format(E'\n  boss #%s bir nechta fermaga biriktirilgan: farms [%s]', r.boss_user_id, r.farm_ids);
    END LOOP;

    -- 3) users.farm_id va farms.boss_user_id bir-biriga mos emas (o'chirilmagan bosslar)
    FOR r IN
        SELECT u.id AS boss_id, u.farm_id AS user_farm_id, f.id AS linked_farm_id, f2.boss_user_id AS farm_boss
        FROM users u
        LEFT JOIN farms f  ON f.boss_user_id = u.id
        LEFT JOIN farms f2 ON f2.id = u.farm_id
        WHERE u.role = 'BOSS' AND u.status <> 'DELETED'
          AND (u.farm_id IS DISTINCT FROM f.id)
    LOOP
        problems := problems || format(E'\n  boss #%s: users.farm_id=%s, lekin farms.boss_user_id bo''yicha ferma=%s (farm #%s ning boss_user_id=%s)',
                                       r.boss_id, COALESCE(r.user_farm_id::text, 'NULL'), COALESCE(r.linked_farm_id::text, 'NULL'),
                                       COALESCE(r.user_farm_id::text, '-'), COALESCE(r.farm_boss::text, 'NULL'));
    END LOOP;

    IF problems <> '' THEN
        RAISE EXCEPTION E'V10 to''xtatildi: boss–ferma bog''lanishida nomuvofiqlik bor. Avval loyiha egasi bilan kelishib tuzating:%', problems
            USING HINT = 'docs/sql/boss_farm_fix_template.sql ga qarang. Standart qiymat avtomatik qo''yilmaydi.';
    END IF;
END $$;

-- Bitta fermaga bitta boss, bitta boss — bitta ferma (TZ §2.1, §21)
CREATE UNIQUE INDEX IF NOT EXISTS uq_farms_boss_user_id ON farms(boss_user_id) WHERE boss_user_id IS NOT NULL;
