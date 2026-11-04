
package com.vallabank.backend.transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WithdrawalResponse(
        UUID transactionId,
        UUID accountId,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        OffsetDateTime createdAt
) {
}
