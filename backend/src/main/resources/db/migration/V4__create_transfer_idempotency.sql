
CREATE TABLE transfer_idempotency (

    idempotency_key UUID PRIMARY KEY,

    from_account_id UUID NOT NULL,
    to_account_id UUID NOT NULL,

    amount NUMERIC(19,2) NOT NULL
        CHECK (amount > 0),

    transfer_id UUID UNIQUE
        REFERENCES transfers(id),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_idempotency_different_accounts
        CHECK (from_account_id <> to_account_id)
);
