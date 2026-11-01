CREATE TABLE user_directory (
                                user_id       UUID         PRIMARY KEY,
                                email         VARCHAR(150) NOT NULL,
                                full_name     VARCHAR(120) NOT NULL,
                                role          VARCHAR(20)  NOT NULL,
                                branch_id     UUID,
                                active        BOOLEAN      NOT NULL DEFAULT TRUE,
                                last_event_at TIMESTAMPTZ  NOT NULL,
                                CONSTRAINT ck_user_directory_role CHECK (role IN ('SYSTEM_ADMIN', 'BRANCH_ADMIN', 'CASHIER', 'DRIVER'))
);

CREATE INDEX ix_user_directory_branch_role ON user_directory (branch_id, role) WHERE active;