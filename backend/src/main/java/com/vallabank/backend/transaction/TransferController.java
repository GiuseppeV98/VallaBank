
package com.vallabank.backend.transaction;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(
            @RequestHeader("Idempotency-Key") UUID idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {

        TransferResponse response = transferService.transfer(
                request,
                idempotencyKey
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
