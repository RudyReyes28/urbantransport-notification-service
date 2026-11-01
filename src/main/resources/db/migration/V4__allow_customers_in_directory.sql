-- Los clientes entran al directorio: los eventos de alquiler y de cartera los identifican por userId o customerId
ALTER TABLE user_directory DROP CONSTRAINT ck_user_directory_role;
ALTER TABLE user_directory ADD CONSTRAINT ck_user_directory_role
    CHECK (role IN ('SYSTEM_ADMIN', 'BRANCH_ADMIN', 'CASHIER', 'DRIVER', 'CUSTOMER'));

ALTER TABLE user_directory ADD COLUMN customer_id UUID;
CREATE INDEX ix_user_directory_customer ON user_directory (customer_id) WHERE customer_id IS NOT NULL;