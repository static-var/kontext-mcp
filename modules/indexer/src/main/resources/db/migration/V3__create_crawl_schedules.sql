CREATE TABLE IF NOT EXISTS crawl_schedules (
    id SERIAL PRIMARY KEY,
    cron_expression TEXT NOT NULL,
    description TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crawl_schedules_enabled
    ON crawl_schedules (enabled);

CREATE OR REPLACE FUNCTION update_crawl_schedules_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_crawl_schedules_updated_at ON crawl_schedules;

CREATE TRIGGER trg_crawl_schedules_updated_at
BEFORE UPDATE ON crawl_schedules
FOR EACH ROW
EXECUTE FUNCTION update_crawl_schedules_timestamp();
