
package com.vallabank.backend.security;

import com.vallabank.backend.customer.Customer;
import com.vallabank.backend.customer.CustomerRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class CustomerCredentialsIntegrationTest {

    @Autowired
    private CustomerRepository customerRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void credentialsAreSavedWithCustomerId() {

        Customer customer = new Customer(
            "Mario",
            "Rossi",
            "credentials-" + UUID.randomUUID() + "@example.com"
        );

        customer = customerRepository.saveAndFlush(customer);

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String passwordHash = encoder.encode("PasswordDiTest123!");

        CustomerCredentials credentials =
            new CustomerCredentials(customer, passwordHash);

        entityManager.persist(credentials);
        entityManager.flush();
        entityManager.clear();

        CustomerCredentials saved =
            entityManager.find(CustomerCredentials.class, customer.getId());

        assertNotNull(saved);

        assertEquals(customer.getId(), saved.getCustomerId());
        assertEquals("CUSTOMER", saved.getRole());
        assertFalse(saved.isEnabled());

        assertTrue(
            encoder.matches("PasswordDiTest123!", saved.getPasswordHash())
        );

        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }
}
