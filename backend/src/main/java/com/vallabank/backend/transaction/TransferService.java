
package com.vallabank.backend.transaction;

import com.vallabank.backend.account.Account;
import com.vallabank.backend.account.AccountRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {

    private static final BigDecimal MAX_BALANCE =
            new BigDecimal("99999999999999999.99");

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final AccountTransactionRepository transactionRepository;
    private final TransferIdempotencyRepository idempotencyRepository;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            AccountTransactionRepository transactionRepository,
            TransferIdempotencyRepository idempotencyRepository
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.transactionRepository = transactionRepository;
        this.idempotencyRepository = idempotencyRepository;
    }

    @Transactional
    public TransferResponse transfer(
            TransferRequest request,
            UUID idempotencyKey
    ) {

        // 1. Validazione
        if (request == null || idempotencyKey == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency key and request are required"
            );
        }

        UUID fromId = request.fromAccountId();
        UUID toId = request.toAccountId();
        BigDecimal amount = request.amount();

        if (fromId == null || toId == null || amount == null
                || amount.signum() <= 0 || amount.scale() > 2
                || amount.precision() - amount.scale() > 17) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid transfer request"
            );
        }

        if (fromId.equals(toId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source and destination must be different"
            );
        }

        // 2. Prenotazione della chiave
        boolean newRequest = idempotencyRepository.reserve(
                idempotencyKey, request
        );

        if (!newRequest) {

            TransferIdempotencyRepository.StoredTransfer previous =
                    idempotencyRepository.findByKey(idempotencyKey);

            // Stessa chiave ma dati diversi
            if (!previous.fromAccountId().equals(fromId)
                    || !previous.toAccountId().equals(toId)
                    || previous.amount().compareTo(amount) != 0) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Idempotency key already used for another request"
                );
            }

            if (previous.transferId() == null
                    || previous.createdAt() == null) {
                throw new IllegalStateException(
                        "Incomplete idempotency record"
                );
            }

            // Restituisce il trasferimento originale
            return new TransferResponse(
                    previous.transferId(),
                    previous.fromAccountId(),
                    previous.toAccountId(),
                    previous.amount(),
                    previous.createdAt()
            );
        }

        // 3. Acquisizione dei lock in ordine stabile
        UUID firstId = fromId.compareTo(toId) < 0 ? fromId : toId;
        UUID secondId = fromId.compareTo(toId) < 0 ? toId : fromId;

        Account first = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"
                ));

        Account second = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"
                ));

        Account source = fromId.equals(firstId) ? first : second;
        Account destination = toId.equals(firstId) ? first : second;

        // 4. Controlli bancari
        if (!"ACTIVE".equals(source.getStatus())
                || !"ACTIVE".equals(destination.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Account is not active"
            );
        }

        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Currency mismatch"
            );
        }

        if (source.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Insufficient funds"
            );
        }

        if (destination.getBalance().add(amount)
                .compareTo(MAX_BALANCE) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Destination balance limit exceeded"
            );
        }

        // 5. Aggiornamento saldi
        source.withdraw(amount);
        destination.deposit(amount);

        // 6. Registrazione trasferimento
        Transfer transfer = new Transfer(fromId, toId, amount);
        transferRepository.saveAndFlush(transfer);

        // 7. Registrazione movimenti
        AccountTransaction outgoing = AccountTransaction.transferOut(
                source, amount, source.getBalance(), transfer.getId()
        );

        AccountTransaction incoming = AccountTransaction.transferIn(
                destination, amount, destination.getBalance(), transfer.getId()
        );

        transactionRepository.save(outgoing);
        transactionRepository.save(incoming);

        // 8. Completamento idempotenza
        idempotencyRepository.complete(
                idempotencyKey,
                transfer.getId()
        );

        // 9. Risposta
        return new TransferResponse(
                transfer.getId(),
                fromId,
                toId,
                amount,
                transfer.getCreatedAt()
        );
    }
}
