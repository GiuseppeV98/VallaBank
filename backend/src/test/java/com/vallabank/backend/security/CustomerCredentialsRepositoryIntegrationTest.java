
package com.vallabank.backend.security;

import com.vallabank.backend.customer.Customer;
import com.vallabank.backend.customer.CustomerRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CustomerCredentialsRepositoryIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerCredentialsRepository credentialsRepository;

    @Test
    void findCredentialsByCustomerEmail() {

        String email = "test-" + UUID.randomUUID()
                + "@example.com";

        Customer customer = new Customer(
                "Mario",
                "Rossi",
                email
        );

        customer = customerRepository.saveAndFlush(customer);

        BCryptPasswordEncoder encoder =
                new BCryptPasswordEncoder();

        String passwordHash =
                encoder.encode("TestPassword123!");

        CustomerCredentials credentials =
                new CustomerCredentials(customer, passwordHash);

        credentialsRepository.saveAndFlush(credentials);

        Optional<CustomerCredentials> found =
                credentialsRepository.findByCustomer_Email(email);

        assertTrue(found.isPresent());

        assertEquals(
                customer.getId(),
                found.get().getCustomerId()
        );

        assertEquals("CUSTOMER", found.get().getRole());

        assertFalse(found.get().isEnabled());

        assertTrue(
                encoder.matches(
                        "TestPassword123!",
                        found.get().getPasswordHash()
                )
        );

        assertTrue(
                credentialsRepository.findById(customer.getId())
                        .isPresent()
        );

        assertTrue(
                credentialsRepository.findByCustomer_Email(
                        "inesistente@example.com"
                ).isEmpty()
        );
    }
}
