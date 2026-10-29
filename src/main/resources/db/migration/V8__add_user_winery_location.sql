ALTER TABLE app_users
    ADD COLUMN winery_name VARCHAR(150),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN region VARCHAR(100),
    ADD COLUMN country VARCHAR(100),
    ADD COLUMN latitude NUMERIC(9,6),
    ADD COLUMN longitude NUMERIC(9,6);

ALTER TABLE app_users
    ADD CONSTRAINT ck_app_users_latitude
        CHECK (latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_app_users_longitude
        CHECK (longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_app_users_location_complete
        CHECK (
            (winery_name IS NULL AND city IS NULL
                AND region IS NULL AND country IS NULL
                AND latitude IS NULL AND longitude IS NULL)
            OR
            (winery_name IS NOT NULL AND city IS NOT NULL
                AND region IS NOT NULL AND country IS NOT NULL
                AND latitude IS NOT NULL AND longitude IS NOT NULL)
        );
