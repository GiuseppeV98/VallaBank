
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

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            AccountTransactionRepository transactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request) {

        UUID fromId = request.fromAccountId();
        UUID toId = request.toAccountId();
        BigDecimal amount = request.amount();

        // 1. Controlli iniziali
        if (fromId == null || toId == null || amount == null
                || amount.signum() <= 0 || amount.scale() > 2
                || amount.precision() - amount.scale() > 17) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Invalid transfer request"
            );
        }

        if (fromId.equals(toId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source and destination must be different"
            );
        }

        // 2. Blocco dei conti in ordine deterministico
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

        // 3. Verifica dello stato
        if (!"ACTIVE".equals(source.getStatus())
                || !"ACTIVE".equals(destination.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Account is not active"
            );
        }

        // 4. Verifica della valuta
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Currency mismatch"
            );
        }

        // 5. Verifica saldo disponibile
        if (source.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Insufficient funds"
            );
        }

        // 6. Verifica limite saldo destinatario
        if (destination.getBalance().add(amount)
                .compareTo(MAX_BALANCE) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Destination balance limit exceeded"
            );
        }

        // 7. Aggiornamento dei saldi
        source.withdraw(amount);
        destination.deposit(amount);

        // 8. Registrazione del trasferimento
        Transfer transfer = new Transfer(fromId, toId, amount);
        transferRepository.saveAndFlush(transfer);

        // 9. Registrazione dei due movimenti
        AccountTransaction outgoing = AccountTransaction.transferOut(
                source, amount, source.getBalance(), transfer.getId()
        );

        AccountTransaction incoming = AccountTransaction.transferIn(
                destination, amount, destination.getBalance(), transfer.getId()
        );

        transactionRepository.save(outgoing);
        transactionRepository.save(incoming);

        // 10. Risposta
        return new TransferResponse(
                transfer.getId(),
                fromId,
                toId,
                amount,
                transfer.getCreatedAt()
        );
    }
}
