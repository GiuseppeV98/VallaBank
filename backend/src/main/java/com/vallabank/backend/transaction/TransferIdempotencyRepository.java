
package com.vallabank.backend.transaction;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public class TransferIdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransferIdempotencyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean reserve(UUID key, TransferRequest request) {

        int inserted = jdbcTemplate.update("""
                INSERT INTO transfer_idempotency (
                    idempotency_key,
                    from_account_id,
                    to_account_id,
                    amount
                )
                VALUES (?, ?, ?, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                key,
                request.fromAccountId(),
                request.toAccountId(),
                request.amount()
        );

        return inserted == 1;
    }

    public StoredTransfer findByKey(UUID key) {

        return jdbcTemplate.queryForObject("""
                SELECT
                    i.from_account_id,
                    i.to_account_id,
                    i.amount,
                    i.transfer_id,
                    t.created_at
                FROM transfer_idempotency i
                LEFT JOIN transfers t ON t.id = i.transfer_id
                WHERE i.idempotency_key = ?
                """,
                (rs, rowNum) -> new StoredTransfer(
                        rs.getObject("from_account_id", UUID.class),
                        rs.getObject("to_account_id", UUID.class),
                        rs.getBigDecimal("amount"),
                        rs.getObject("transfer_id", UUID.class),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                key
        );
    }

    public void complete(UUID key, UUID transferId) {

        int updated = jdbcTemplate.update("""
                UPDATE transfer_idempotency
                SET transfer_id = ?
                WHERE idempotency_key = ?
                  AND transfer_id IS NULL
                """,
                transferId,
                key
        );

        if (updated != 1) {
            throw new IllegalStateException(
                    "Could not complete idempotent transfer"
            );
        }
    }

    public record StoredTransfer(
            UUID fromAccountId,
            UUID toAccountId,
            BigDecimal amount,
            UUID transferId,
            OffsetDateTime createdAt
    ) {
    }
}
