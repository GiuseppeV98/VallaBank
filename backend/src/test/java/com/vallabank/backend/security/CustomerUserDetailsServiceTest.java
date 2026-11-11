
package com.vallabank.backend.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerUserDetailsServiceTest {

    @Mock
    private CustomerCredentialsRepository credentialsRepository;

    @Mock
    private CustomerCredentials credentials;

    @InjectMocks
    private CustomerUserDetailsService userDetailsService;

    @Test
    void enabledCustomerCanBeLoaded() {

        String email = "cliente@example.com";

        when(credentialsRepository.findByCustomer_Email(email))
                .thenReturn(Optional.of(credentials));

        when(credentials.getPasswordHash())
                .thenReturn("{bcrypt}$2a$10$hash-simulato");

        when(credentials.getRole())
                .thenReturn("CUSTOMER");

        when(credentials.isEnabled())
                .thenReturn(true);

        UserDetails user =
                userDetailsService.loadUserByUsername(email);

        assertEquals(email, user.getUsername());

        assertTrue(user.isEnabled());

        assertTrue(
                user.getAuthorities().stream()
                        .anyMatch(authority ->
                                authority.getAuthority()
                                        .equals("ROLE_CUSTOMER")
                        )
        );

        assertEquals(
                "{bcrypt}$2a$10$hash-simulato",
                user.getPassword()
        );
    }

    @Test
    void disabledCustomerCannotAuthenticate() {

        String email = "disabilitato@example.com";

        when(credentialsRepository.findByCustomer_Email(email))
                .thenReturn(Optional.of(credentials));

        when(credentials.getPasswordHash())
                .thenReturn("{bcrypt}$2a$10$hash-simulato");

        when(credentials.getRole())
                .thenReturn("CUSTOMER");

        when(credentials.isEnabled())
                .thenReturn(false);

        UserDetails user =
                userDetailsService.loadUserByUsername(email);

        assertFalse(user.isEnabled());
    }

    @Test
    void unknownCustomerThrowsException() {

        String email = "inesistente@example.com";

        when(credentialsRepository.findByCustomer_Email(email))
                .thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername(email)
        );
    }
}
