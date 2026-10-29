ALTER TABLE device_configurations
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at TIMESTAMPTZ,
    ADD COLUMN dispatch_lease_until TIMESTAMPTZ;

ALTER TABLE device_configurations
    ADD CONSTRAINT ck_configuration_attempt_count
    CHECK (attempt_count >= 0);
