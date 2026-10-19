-- V15: Add shift_type to users for staff shift tracking (DAY, NIGHT, FULL_TIME, etc.)
ALTER TABLE users ADD COLUMN IF NOT EXISTS shift_type VARCHAR(50);
