package com.vallabank.backend.account;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        UUID customerId,
        String iban,
        String accountType,
        String currency,
        BigDecimal balance,
        String status
) {
}