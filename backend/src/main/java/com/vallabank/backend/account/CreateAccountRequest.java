package com.vallabank.backend.account;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(
        @NotNull 
        UUID customerId,
        @NotBlank 
        @ValidIban
        String iban
) {
}