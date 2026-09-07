CREATE TABLE customers (
    id UUID PRIMARY KEY,

    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_customers_email UNIQUE (email)
);


CREATE TABLE accounts (
    id UUID PRIMARY KEY,

    customer_id UUID NOT NULL,

    iban VARCHAR(34) NOT NULL,

    account_type VARCHAR(20) NOT NULL DEFAULT 'CHECKING',

    currency CHAR(3) NOT NULL DEFAULT 'EUR',

    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    version BIGINT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_accounts_iban UNIQUE (iban),

    CONSTRAINT fk_accounts_customer
        FOREIGN KEY (customer_id)
        REFERENCES customers(id),

    CONSTRAINT chk_accounts_type
        CHECK (account_type IN ('CHECKING', 'SAVINGS')),

    CONSTRAINT chk_accounts_status
        CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED')),

    CONSTRAINT chk_accounts_currency
        CHECK (currency ~ '^[A-Z]{3}$')
);


CREATE INDEX idx_accounts_customer_id
    ON accounts(customer_id);