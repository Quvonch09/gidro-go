-- ============================================================================
-- GidroGo Migration V2: Client to Farm Binding for per-firm CRM model
-- ============================================================================

ALTER TABLE clients ADD COLUMN IF NOT EXISTS farm_id BIGINT REFERENCES farms(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_clients_farm ON clients(farm_id);
