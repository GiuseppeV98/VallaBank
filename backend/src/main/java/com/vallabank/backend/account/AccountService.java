package com.vallabank.backend.account;

import com.vallabank.backend.customer.Customer;
import com.vallabank.backend.customer.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountService(
            AccountRepository accountRepository,
            CustomerRepository customerRepository
    ) {
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    public Account createAccount(UUID customerId, String iban) {
        if (accountRepository.existsByIban(iban)) {
            throw new DuplicateIbanException();
        }

        Customer customer = customerRepository.findById(customerId)
        .orElseThrow(CustomerNotFoundException::new);
        
        Account account = new Account(customer, iban);

        return accountRepository.save(account);
    }

    public Optional<Account> getAccountById(UUID id) {
        return accountRepository.findById(id);
    }
}