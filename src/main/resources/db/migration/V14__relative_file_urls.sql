-- ============================================================================
-- V14 (BE-13 / BUG-012): Fayl URL'larini nisbiy yo'lga o'tkazish
--
-- http://<host>[:port]/api/files/view/...  ->  /api/files/view/...
-- (HTTPS sahifada brauzer http:// rasmlarni bloklaydi.)
-- https://gidrogo.uz/assets/*  -> NULL (bu fayllar mavjud emas, yuklanmaydi; asl qiymat NOTICE'da qoladi).
-- Avval ro'yxat (RAISE NOTICE — Flyway logida ko'rinadi), keyin almashtirish.
-- ============================================================================

DO $$
DECLARE
    r RECORD;
    n INT := 0;
BEGIN
    FOR r IN
        SELECT 'farms.logo_url' AS col, id, logo_url AS url FROM farms
            WHERE logo_url ~ '^https?://[^/]+/api/files/view/' OR logo_url LIKE 'https://gidrogo.uz/assets/%'
        UNION ALL
        SELECT 'users.avatar_url', id, avatar_url FROM users
            WHERE avatar_url ~ '^https?://[^/]+/api/files/view/' OR avatar_url LIKE 'https://gidrogo.uz/assets/%'
        UNION ALL
        SELECT 'products.image_url', id, image_url FROM products
            WHERE image_url ~ '^https?://[^/]+/api/files/view/' OR image_url LIKE 'https://gidrogo.uz/assets/%'
        UNION ALL
        SELECT 'banners.image_url', id, image_url FROM banners
            WHERE image_url ~ '^https?://[^/]+/api/files/view/' OR image_url LIKE 'https://gidrogo.uz/assets/%'
        UNION ALL
        SELECT 'delivery_photos.photo_url', id, photo_url FROM delivery_photos
            WHERE photo_url ~ '^https?://[^/]+/api/files/view/'
    LOOP
        RAISE NOTICE 'V14 URL: % #% = %', r.col, r.id, r.url;
        n := n + 1;
    END LOOP;
    RAISE NOTICE 'V14: jami % ta mutlaq URL topildi', n;
END $$;

UPDATE farms    SET logo_url   = regexp_replace(logo_url,   '^https?://[^/]+(/api/files/view/)', '\1') WHERE logo_url   ~ '^https?://[^/]+/api/files/view/';
UPDATE users    SET avatar_url = regexp_replace(avatar_url, '^https?://[^/]+(/api/files/view/)', '\1') WHERE avatar_url ~ '^https?://[^/]+/api/files/view/';
UPDATE products SET image_url  = regexp_replace(image_url,  '^https?://[^/]+(/api/files/view/)', '\1') WHERE image_url  ~ '^https?://[^/]+/api/files/view/';
UPDATE banners  SET image_url  = regexp_replace(image_url,  '^https?://[^/]+(/api/files/view/)', '\1') WHERE image_url  ~ '^https?://[^/]+/api/files/view/';
-- delivery_photos.photo_url NOT NULL — faqat prefiks almashtiriladi
UPDATE delivery_photos SET photo_url = regexp_replace(photo_url, '^https?://[^/]+(/api/files/view/)', '\1') WHERE photo_url ~ '^https?://[^/]+/api/files/view/';

UPDATE farms    SET logo_url   = NULL WHERE logo_url   LIKE 'https://gidrogo.uz/assets/%';
UPDATE users    SET avatar_url = NULL WHERE avatar_url LIKE 'https://gidrogo.uz/assets/%';
UPDATE products SET image_url  = NULL WHERE image_url  LIKE 'https://gidrogo.uz/assets/%';
UPDATE banners  SET image_url  = NULL WHERE image_url  LIKE 'https://gidrogo.uz/assets/%';
