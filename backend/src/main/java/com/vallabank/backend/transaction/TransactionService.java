
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

}
