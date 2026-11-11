
CREATE TABLE customer_credentials (

    customer_id UUID PRIMARY KEY,

    password_hash VARCHAR(255) NOT NULL,

    role VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER',

    enabled BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_customer_credentials_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id),

    CONSTRAINT chk_customer_credentials_role
        CHECK (role = 'CUSTOMER'),

    CONSTRAINT chk_customer_credentials_password_hash_not_blank
        CHECK (length(btrim(password_hash)) > 0)
);
