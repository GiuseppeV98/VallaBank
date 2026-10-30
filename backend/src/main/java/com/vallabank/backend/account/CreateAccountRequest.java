package com.vallabank.backend.account;

import java.util.UUID;

public record CreateAccountRequest(
        UUID customerId,
        String iban
) {
}