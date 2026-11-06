
package com.vallabank.backend.transaction;

import com.vallabank.backend.account.Account;
import com.vallabank.backend.account.AccountRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.List;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final AccountTransactionRepository transactionRepository;

    public TransactionService(
            AccountRepository accountRepository,
            AccountTransactionRepository transactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public DepositResponse deposit(UUID accountId, BigDecimal amount) {

        // 1. Verifica dell'importo
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid deposit amount"
            );
        }

        // 2. Recupero del conto
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Account not found"
                ));

        // 3. Controllo dello stato del conto
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Account is not active"
            );
        }

        // 4. Aggiornamento del saldo
        account.deposit(amount);

        // 5. Creazione del movimento
        AccountTransaction transaction = new AccountTransaction(
                account,
                amount,
                account.getBalance()
        );

        // 6. Salvataggio del movimento
        transactionRepository.save(transaction);

        // 7. Preparazione della risposta

return new DepositResponse(
        transaction.getId(),
        account.getId(),
        transaction.getTransactionType(),
        transaction.getAmount(),
        transaction.getBalanceAfter(),
        transaction.getCreatedAt()
);

    }
    
@Transactional
public WithdrawalResponse withdraw(UUID accountId, BigDecimal amount) {

    // 1. Validazione dell'importo
    if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid withdrawal amount"
        );
    }

    // 2. Recupero del conto
    Account account = accountRepository.findById(accountId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Account not found"
            ));

    // 3. Controllo dello stato
    if (!"ACTIVE".equals(account.getStatus())) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Account is not active"
        );
    }

    // 4. Verifica disponibilità del saldo
    if (account.getBalance().compareTo(amount) < 0) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Insufficient funds"
        );
    }

    // 5. Sottrazione dell'importo
    account.withdraw(amount);

    // 6. Creazione e registrazione del movimento
    AccountTransaction transaction = AccountTransaction.withdrawal(
            account,
            amount,
            account.getBalance()
    );

    transactionRepository.save(transaction);

    // 7. Preparazione della risposta
    return new WithdrawalResponse(
            transaction.getId(),
            account.getId(),
            transaction.getTransactionType(),
            transaction.getAmount(),
            transaction.getBalanceAfter(),
            transaction.getCreatedAt()
    );
}

@Transactional(readOnly = true)
public TransactionHistoryResponse getAccountTransactions(
        UUID accountId,
        int page,
        int size
) {

    // 1. Validazione dei parametri di paginazione
    if (page < 0 || size < 1 || size > 100) {
        throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid pagination parameters"
        );
    }

    // 2. Verifica che il conto esista
    if (!accountRepository.existsById(accountId)) {
        throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Account not found"
        );
    }

    // 3. Definizione della pagina e ordinamento
    PageRequest pageable = PageRequest.of(
            page,
            size,
            Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            )
    );

    // 4. Recupero dei movimenti da PostgreSQL
    Page<AccountTransaction> result =
            transactionRepository.findByAccount_Id(
                    accountId,
                    pageable
            );

    // 5. Conversione Entity -> DTO
    
List<TransactionItemResponse> transactions =
        result.getContent().stream()
                .map(transaction -> new TransactionItemResponse(
                        transaction.getId(),
                        transaction.getTransactionType(),
                        transaction.getAmount(),
                        transaction.getBalanceAfter(),
                        transaction.getCreatedAt(),
                        transaction.getTransferId()
                ))
                .toList();


    // 6. Costruzione della risposta
    return new TransactionHistoryResponse(
            accountId,
            transactions,
            page,
            size,
            result.getTotalElements(),
            result.getTotalPages()
    );
}

}
