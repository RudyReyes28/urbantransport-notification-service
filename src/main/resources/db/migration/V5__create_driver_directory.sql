CREATE TABLE driver_directory (
                                  driver_id          UUID         PRIMARY KEY,
                                  user_id            UUID,
                                  full_name          VARCHAR(150) NOT NULL,
                                  email              VARCHAR(150) NOT NULL,
                                  branch_id          UUID         NOT NULL,
                                  license_number     VARCHAR(30)  NOT NULL,
                                  license_expires_on DATE         NOT NULL,
                                  active             BOOLEAN      NOT NULL DEFAULT TRUE,
                                  last_event_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_driver_directory_expiry ON driver_directory (license_expires_on) WHERE active;