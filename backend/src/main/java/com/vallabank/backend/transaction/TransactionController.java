
package com.vallabank.backend.transaction;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/{id}/deposits")
    public ResponseEntity<DepositResponse> deposit(
            @PathVariable UUID id,
            @Valid @RequestBody DepositRequest request
    ) {

        DepositResponse response = transactionService.deposit(
                id,
                request.amount()
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
