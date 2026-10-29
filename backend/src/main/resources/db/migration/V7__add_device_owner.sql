ALTER TABLE devices
    ADD COLUMN owner_id BIGINT;

ALTER TABLE devices
    ADD CONSTRAINT fk_devices_owner
    FOREIGN KEY (owner_id)
    REFERENCES app_users(id);

CREATE INDEX idx_devices_owner
    ON devices(owner_id);
