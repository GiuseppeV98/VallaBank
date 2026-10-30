package com.vallabank.backend.account;
import com.vallabank.backend.customer.Customer;
import com.vallabank.backend.customer.CustomerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class AccountServiceIntegrationTest {
@Autowired
private AccountService accountService;

@Autowired
private AccountRepository accountRepository;

@Autowired
private CustomerRepository customerRepository;

@Autowired
private EntityManager entityManager;


    @Test
    void createAccountPersistsAccountForExistingCustomer() {

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);

        Customer customer = new Customer(
                "Test",
                "Account",
                "account." + suffix + "@example.com"
        );

        customer = customerRepository.save(customer);

        UUID customerId = customer.getId();

        String iban = "IT60X0542811101" + suffix.toUpperCase();

        Account account = accountService.createAccount(customerId, iban);

        accountRepository.flush();
        entityManager.clear();

        Optional<Account> savedAccount =
                accountRepository.findById(account.getId());

        assertTrue(savedAccount.isPresent());

        Account found = savedAccount.get();

        assertEquals(customerId, found.getCustomer().getId());
        assertEquals(iban, found.getIban());
        assertEquals("CHECKING", found.getAccountType());
        assertEquals("EUR", found.getCurrency());
        assertEquals(new BigDecimal("0.00"), found.getBalance());
        assertEquals("ACTIVE", found.getStatus());
    }

@Test
void createAccountRejectsDuplicateIban() {

    String suffix = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 10);

    Customer customer = new Customer(
            "Test",
            "Duplicate",
            "duplicate." + suffix + "@example.com"
    );

    customer = customerRepository.save(customer);

    UUID customerId = customer.getId();

    String iban = "IT60X0542811102" + suffix.toUpperCase();

    accountService.createAccount(customerId, iban);

    IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> accountService.createAccount(customerId, iban)
    );

    assertEquals("IBAN already exists", exception.getMessage());
}
@Test
void createAccountRejectsMissingCustomer() {

    UUID missingCustomerId = UUID.randomUUID();

    String iban = "IT60X0542811103000000000000";

    IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> accountService.createAccount(missingCustomerId, iban)
    );

    assertEquals("Customer not found", exception.getMessage());
}
}