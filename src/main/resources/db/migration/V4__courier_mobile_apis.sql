-- V4: Courier Mobile App APIs - additional fields and indexes

-- Kuryer rad etishi uchun: SEARCHING status ga qaytarilgan buyurtmalarni boshqarish
-- OrderStatus enum da allaqachon SEARCHING mavjud

-- Buyurtmada client_note saqlash (kuryer yetkazib berishda mijoz izohi)
ALTER TABLE orders ADD COLUMN IF NOT EXISTS client_note TEXT;

-- Kuryer mashinadagi yukni kuzatish uchun indekslar
CREATE INDEX IF NOT EXISTS idx_orders_courier_status ON orders(courier_id, status);
CREATE INDEX IF NOT EXISTS idx_orders_farm_created ON orders(farm_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_client_farm ON orders(client_id, farm_id);

-- Kuryer lokatsiya va online holat uchun users jadvalida hech narsa o'zgarmaydi
-- (Redis ishlatiladi)

-- Delivery photos uchun indeks
CREATE INDEX IF NOT EXISTS idx_delivery_photos_order ON delivery_photos(order_id);

-- Order problem logs uchun indeks
CREATE INDEX IF NOT EXISTS idx_problem_logs_order ON order_problem_logs(order_id);
