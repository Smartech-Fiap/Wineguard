ALTER TABLE devices
    ADD COLUMN mqtt_device_id VARCHAR(100);

UPDATE devices
SET mqtt_device_id = 'wine002'
WHERE fiware_id = 'WineSensor002'
  AND fiware_type = 'WineSensor'
  AND fiware_service = 'vinheria'
  AND fiware_service_path = '/';

ALTER TABLE devices
    ADD CONSTRAINT uk_devices_mqtt
    UNIQUE (fiware_service, fiware_service_path, mqtt_device_id);

ALTER TABLE devices
    ADD CONSTRAINT ck_devices_mqtt_id
    CHECK (
        mqtt_device_id IS NULL
        OR mqtt_device_id ~ '^[A-Za-z0-9_-]{1,100}$'
    );
