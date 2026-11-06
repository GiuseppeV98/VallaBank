
package com.vallabank.backend.transaction;

import com.jayway.jsonpath.JsonPath;
import com.vallabank.backend.account.AccountRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransferConcurrencyIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountTransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> accountIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();

    @Test
    void concurrentTransfersCannotSpendSameBalanceTwice()
            throws Exception {

        // 1. Prepariamo tre conti distinti
        UUID source = createAccount();
        UUID destinationB = createAccount();
        UUID destinationC = createAccount();

        // 2. Depositiamo 100 euro sul conto sorgente
        mockMvc.perform(post("/api/accounts/{id}/deposits", source)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":100.00}"))
                .andExpect(status().isCreated());

        // 3. Prepariamo due thread
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            Future<Integer> first = executor.submit(() ->
                    attemptTransfer(
                            source, destinationB, ready, start
                    )
            );

            Future<Integer> second = executor.submit(() ->
                    attemptTransfer(
                            source, destinationC, ready, start
                    )
            );

            // Aspettiamo che entrambi i thread siano pronti
            assertTrue(ready.await(10, TimeUnit.SECONDS));

            // Li facciamo partire insieme
            start.countDown();

            int result1 = first.get(20, TimeUnit.SECONDS);
            int result2 = second.get(20, TimeUnit.SECONDS);

            // Un trasferimento deve riuscire e uno fallire
            assertEquals(
                    List.of(201, 409),
                    List.of(result1, result2).stream().sorted().toList()
            );

            // 4. Il mittente deve avere 20 euro
            assertEquals(
                    0,
                    balance(source).compareTo(new BigDecimal("20.00"))
            );

            // 5. I destinatari devono avere 80 euro totali
            BigDecimal received = balance(destinationB)
                    .add(balance(destinationC));

            assertEquals(
                    0,
                    received.compareTo(new BigDecimal("80.00"))
            );

            // 6. Deve esserci un solo movimento TRANSFER_OUT
            long outgoing = transactionRepository
                    .findByAccount_Id(source, Pageable.unpaged())
                    .getContent()
                    .stream()
                    .filter(t -> "TRANSFER_OUT".equals(
                            t.getTransactionType()
                    ))
                    .count();

            assertEquals(1, outgoing);

        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(
                    20, TimeUnit.SECONDS
            ));
        }
    }

    private int attemptTransfer(
            UUID source,
            UUID destination,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Start timeout");
        }

        try {
           transferService.transfer(
        new TransferRequest(
                source,
                destination,
                new BigDecimal("80.00")
        ),
        UUID.randomUUID()
);

            return 201;

        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        }
    }

    private BigDecimal balance(UUID accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow()
                .getBalance();
    }

    private UUID createAccount() throws Exception {

        String customerJson = """
                {
                    "firstName": "Concurrency",
                    "lastName": "Test",
                    "email": "concurrency-%s@example.com"
                }
                """.formatted(UUID.randomUUID());

        String customerResponse = mockMvc.perform(
                post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson)
        )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String customerId = JsonPath.read(
                customerResponse, "$.id"
        );

        customerIds.add(UUID.fromString(customerId));

        String accountJson = """
                {
                    "customerId": "%s",
                    "iban": "%s"
                }
                """.formatted(customerId, generateTestIban());

        String accountResponse = mockMvc.perform(
                post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson)
        )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID accountId = UUID.fromString(
                JsonPath.read(accountResponse, "$.id")
        );

        accountIds.add(accountId);
        return accountId;
    }

    private String generateTestIban() {

        long number = Math.floorMod(
                UUID.randomUUID().getMostSignificantBits(),
                1_000_000_000_000L
        );

        String bban = "X0542811101"
                + String.format(Locale.ROOT, "%012d", number);

        String rearranged = bban + "IT00";

        int remainder = 0;

        for (char c : rearranged.toCharArray()) {
            if (c >= '0' && c <= '9') {
                remainder = (remainder * 10 + c - '0') % 97;
            } else {
                int value = c - 'A' + 10;
                remainder = (remainder * 100 + value) % 97;
            }
        }

        return "IT"
                + String.format(Locale.ROOT, "%02d", 98 - remainder)
                + bban;
    }

@AfterEach
void cleanup() {

    // 1. Eliminiamo i movimenti bancari
    for (UUID id : accountIds) {
        jdbcTemplate.update(
                "DELETE FROM account_transactions WHERE account_id = ?",
                id
        );
    }

    // 2. Eliminiamo i riferimenti di idempotenza
    for (UUID id : accountIds) {
        jdbcTemplate.update(
                "DELETE FROM transfer_idempotency WHERE from_account_id = ? OR to_account_id = ?",
                id, id
        );
    }

    // 3. Eliminiamo i trasferimenti
    for (UUID id : accountIds) {
        jdbcTemplate.update(
                "DELETE FROM transfers WHERE from_account_id = ? OR to_account_id = ?",
                id, id
        );
    }

    // 4. Eliminiamo i conti bancari
    for (UUID id : accountIds) {
        jdbcTemplate.update(
                "DELETE FROM accounts WHERE id = ?",
                id
        );
    }

    // 5. Eliminiamo i clienti
    for (UUID id : customerIds) {
        jdbcTemplate.update(
                "DELETE FROM customers WHERE id = ?",
                id
        );
    }
}

@Test
void concurrentRequestsWithSameKeyExecuteTransferOnlyOnce()
        throws Exception {

    // 1. Creiamo due conti
    UUID source = createAccount();
    UUID destination = createAccount();

    // 2. Depositiamo 100 euro sul conto sorgente
    mockMvc.perform(post("/api/accounts/{id}/deposits", source)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":100.00}"))
            .andExpect(status().isCreated());

    // 3. Stessa chiave per entrambe le richieste
    UUID idempotencyKey = UUID.randomUUID();

    ExecutorService executor = Executors.newFixedThreadPool(2);

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);

    // 4. Prepariamo la richiesta comune
    Callable<TransferResponse> task = () -> {

        ready.countDown();

        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Start timeout");
        }

        return transferService.transfer(
                new TransferRequest(
                        source,
                        destination,
                        new BigDecimal("40.00")
                ),
                idempotencyKey
        );
    };

    try {

        // 5. Lanciamo due richieste parallele
        Future<TransferResponse> first = executor.submit(task);
        Future<TransferResponse> second = executor.submit(task);

        assertTrue(ready.await(10, TimeUnit.SECONDS));

        start.countDown();

        TransferResponse result1 = first.get(20, TimeUnit.SECONDS);
        TransferResponse result2 = second.get(20, TimeUnit.SECONDS);

        // 6. Entrambe devono riferirsi allo stesso trasferimento
        assertNotNull(result1.transferId());
        assertEquals(result1.transferId(), result2.transferId());

        // 7. Il mittente deve avere 60 euro
        assertEquals(
                0,
                balance(source).compareTo(new BigDecimal("60.00"))
        );

        // 8. Il destinatario deve avere 40 euro
        assertEquals(
                0,
                balance(destination).compareTo(new BigDecimal("40.00"))
        );

        // 9. Devono esistere soltanto due movimenti
        List<AccountTransaction> movements =
                transactionRepository.findByTransferId(
                        result1.transferId()
                );

        assertEquals(2, movements.size());

    } finally {

        start.countDown();
        executor.shutdownNow();

        assertTrue(executor.awaitTermination(
                20, TimeUnit.SECONDS
        ));
    }
}

}
