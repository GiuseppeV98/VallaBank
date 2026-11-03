
CREATE TABLE account_transactions (

    id UUID PRIMARY KEY,

    account_id UUID NOT NULL
        REFERENCES accounts(id),

    transaction_type VARCHAR(20) NOT NULL,

    amount NUMERIC(19,2) NOT NULL
        CHECK (amount > 0),

    balance_after NUMERIC(19,2) NOT NULL
        CHECK (balance_after >= 0),

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT chk_account_transaction_type
        CHECK (transaction_type IN (
            'DEPOSIT',
            'WITHDRAWAL',
            'TRANSFER_IN',
            'TRANSFER_OUT'
        ))
);

CREATE INDEX idx_account_transactions_account_created
ON account_transactions (account_id, created_at DESC);
