
package com.vallabank.backend.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerCredentialsRepository
        extends JpaRepository<CustomerCredentials, UUID> {

    Optional<CustomerCredentials> findByCustomer_Email(String email);
}
