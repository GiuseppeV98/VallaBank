
package com.vallabank.backend.security;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PasswordEncoderIntegrationTest {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void passwordIsEncodedAndCanBeVerified() {

        String password = "TestPassword123!";

        String hash = passwordEncoder.encode(password);

        assertNotEquals(password, hash);

        assertTrue(hash.startsWith("{bcrypt}"));

        assertTrue(passwordEncoder.matches(password, hash));

        assertFalse(
            passwordEncoder.matches("PasswordSbagliata!", hash)
        );
    }

    @Test
    void samePasswordProducesDifferentHashes() {

        String password = "TestPassword123!";

        String firstHash = passwordEncoder.encode(password);
        String secondHash = passwordEncoder.encode(password);

        assertNotEquals(firstHash, secondHash);

        assertTrue(passwordEncoder.matches(password, firstHash));

        assertTrue(passwordEncoder.matches(password, secondHash));
    }
}
