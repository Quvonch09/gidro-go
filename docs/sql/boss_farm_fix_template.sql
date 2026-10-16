-- ============================================================================
-- BE-03 / BUG-016: boss–ferma nomuvofiqligini QO'LDA tuzatish namunasi
--
-- V10 migratsiyasi nomuvofiq juftlar bo'lsa to'xtaydi va ro'yxatni chiqaradi.
-- Bu skript AVTOMATIK ishga tushirilmaydi. Har bir juft bo'yicha qarorni loyiha egasi beradi.
-- Prod bazada ishlatishdan oldin zaxira nusxa oling va avval 1-qadamni ishga tushirib natijani ko'ring.
-- ============================================================================

-- 1-qadam: diagnostika (faqat o'qish) ---------------------------------------
-- a) Ferma boss sifatida boss bo'lmagan yoki o'chirilgan foydalanuvchini ko'rsatadi
SELECT f.id AS farm_id, f.name, f.boss_user_id, u.role, u.status
FROM farms f LEFT JOIN users u ON u.id = f.boss_user_id
WHERE f.boss_user_id IS NOT NULL AND (u.id IS NULL OR u.role <> 'BOSS' OR u.status = 'DELETED');

-- b) Bitta boss bir nechta fermada
SELECT boss_user_id, array_agg(id ORDER BY id) AS farm_ids
FROM farms WHERE boss_user_id IS NOT NULL GROUP BY boss_user_id HAVING COUNT(*) > 1;

-- c) users.farm_id ≠ farms.boss_user_id (masalan, audit: boss #33 farmId=10, lekin farm #10 bossUserId=null)
SELECT u.id AS boss_id, u.full_name, u.phone, u.farm_id AS users_farm_id,
       f.id AS farm_by_boss_user_id, f2.boss_user_id AS farm_of_users_farm_id_boss
FROM users u
LEFT JOIN farms f  ON f.boss_user_id = u.id
LEFT JOIN farms f2 ON f2.id = u.farm_id
WHERE u.role = 'BOSS' AND u.status <> 'DELETED' AND u.farm_id IS DISTINCT FROM f.id;

-- 2-qadam: egasi tasdiqlagan tuzatish (misol: #33 ↔ #10) -------------------
-- Variant A — boss #33 haqiqatan ferma #10 ning bossi (fermada boshqa boss yo'q bo'lsa):
-- BEGIN;
--   UPDATE farms SET boss_user_id = 33 WHERE id = 10 AND boss_user_id IS NULL;
--   -- Agar #33 boshqa fermada ham ko'rsatilgan bo'lsa, o'sha bog'lanishni uzing:
--   -- UPDATE farms SET boss_user_id = NULL WHERE boss_user_id = 33 AND id <> 10;
-- COMMIT;
--
-- Variant B — boss #33 hech qaysi fermaga tegishli emas:
-- BEGIN;
--   UPDATE users SET farm_id = NULL WHERE id = 33 AND role = 'BOSS';
-- COMMIT;
--
-- 3-qadam: 1-qadamdagi so'rovlar bo'sh natija berishi kerak, so'ng ilovani qayta ishga tushiring —
-- Flyway V10..V14 ni qo'llaydi.
