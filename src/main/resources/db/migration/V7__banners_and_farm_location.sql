-- ============================================================================
-- V7: Banners table & Farm City/District/Coverage Areas columns
-- ============================================================================

-- 1. Banners Table (Mijoz bosh sahifasi slayder va promo bannerlari)
CREATE TABLE IF NOT EXISTS banners (
    id             BIGSERIAL PRIMARY KEY,
    badge_text     VARCHAR(100),
    title          VARCHAR(255) NOT NULL,
    subtitle       VARCHAR(500),
    image_url      VARCHAR(500),
    gradient_start VARCHAR(50) DEFAULT '#0284C7',
    gradient_end   VARCHAR(50) DEFAULT '#1D61F2',
    icon_name      VARCHAR(100) DEFAULT 'water_drop',
    action_type    VARCHAR(50) DEFAULT 'NONE',
    action_value   VARCHAR(500),
    sort_order     INTEGER NOT NULL DEFAULT 1,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    farm_id        BIGINT REFERENCES farms(id) ON DELETE CASCADE,
    type           VARCHAR(50) NOT NULL DEFAULT 'HOME_SLIDER',
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_banners_type_active ON banners(type, is_active, sort_order);

-- Insert default promo banner if not exists
INSERT INTO banners (badge_text, title, subtitle, image_url, gradient_start, gradient_end, icon_name, action_type, action_value, sort_order, is_active, type)
SELECT 'BEPUL YETKAZIB BERISH', 'Toza va mineralli suv bir zumda eshigingizda!', 'Eng yaqin fermalardan 20 daqiqada', NULL, '#0284C7', '#1D61F2', 'water_drop', 'NONE', NULL, 1, TRUE, 'HOME_SLIDER'
WHERE NOT EXISTS (SELECT 1 FROM banners WHERE type = 'HOME_SLIDER');

-- 2. Add City, District and Coverage Areas columns to farms
ALTER TABLE farms ADD COLUMN IF NOT EXISTS city VARCHAR(100);
ALTER TABLE farms ADD COLUMN IF NOT EXISTS district VARCHAR(100);
ALTER TABLE farms ADD COLUMN IF NOT EXISTS coverage_areas TEXT;

-- Initial data population for existing farms
UPDATE farms SET
    city = 'Samarqand',
    district = 'Registon',
    coverage_areas = 'Samarqand, Bog''ishamol tumani, Siyob tumani, Registon'
WHERE (LOWER(name) LIKE '%samarqand%' OR LOWER(address) LIKE '%samarqand%' OR LOWER(address) LIKE '%registon%')
  AND (city IS NULL OR city = '');

UPDATE farms SET
    city = 'Toshkent',
    district = 'Yunusobod tumani',
    coverage_areas = 'Toshkent, Yunusobod tumani, Mirzo Ulug''bek tumani, Chilonzor tumani'
WHERE (LOWER(name) LIKE '%yunusobod%' OR LOWER(address) LIKE '%yunusobod%')
  AND (city IS NULL OR city = '');

UPDATE farms SET
    city = 'Toshkent',
    district = 'Chilonzor tumani',
    coverage_areas = 'Toshkent, Chilonzor tumani, Yunusobod tumani, Shayxontohur tumani'
WHERE (LOWER(name) LIKE '%chilonzor%' OR LOWER(address) LIKE '%chilonzor%')
  AND (city IS NULL OR city = '');

UPDATE farms SET
    city = 'Toshkent',
    district = 'Shayxontohur tumani',
    coverage_areas = 'Toshkent, Shayxontohur tumani, Yunusobod tumani, Chilonzor tumani'
WHERE (LOWER(name) LIKE '%toshkent%' OR LOWER(address) LIKE '%toshkent%' OR LOWER(name) LIKE '%tashkent%' OR LOWER(address) LIKE '%tashkent%')
  AND (city IS NULL OR city = '');

UPDATE farms SET
    city = 'Qarshi',
    district = 'Nasaf tumani',
    coverage_areas = 'Qarshi, Nasaf tumani, Qashqadaryo'
WHERE (LOWER(name) LIKE '%qarshi%' OR LOWER(address) LIKE '%qarshi%' OR LOWER(address) LIKE '%qashqadaryo%')
  AND (city IS NULL OR city = '');
