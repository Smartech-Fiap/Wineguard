ALTER TABLE app_users
    ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';

ALTER TABLE app_users
    ADD CONSTRAINT ck_app_user_role
    CHECK (role IN ('ADMIN', 'USER'));
