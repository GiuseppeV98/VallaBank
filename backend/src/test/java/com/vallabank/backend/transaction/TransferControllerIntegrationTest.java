
package com.vallabank.backend.transaction;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransferControllerIntegrationTest {

    private static final String IBAN_A =
            "IT10X0542811101000000000002";

    private static final String IBAN_B =
            "IT80X0542811101000000000003";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountTransactionRepository transactionRepository;

    private String createAccount(String iban) throws Exception {

        String customerJson = """
                {
                    "firstName": "Transfer",
                    "lastName": "Test",
                    "email": "transfer-%s@example.com"
                }
                """.formatted(UUID.randomUUID());

        String customerResponse = mockMvc.perform(
                post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(customerJson)
        )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String customerId = JsonPath.read(customerResponse, "$.id");

        String accountJson = """
                {
                    "customerId": "%s",
                    "iban": "%s"
                }
                """.formatted(customerId, iban);

        String accountResponse = mockMvc.perform(
                post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson)
        )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.read(accountResponse, "$.id");
    }

    private String transferJson(
            String fromId,
            String toId,
            String amount
    ) {
        return """
                {
                    "fromAccountId": "%s",
                    "toAccountId": "%s",
                    "amount": %s
                }
                """.formatted(fromId, toId, amount);
    }

    @Test
    void transferUpdatesBalancesAndCreatesTwoMovements() throws Exception {

        String fromId = createAccount(IBAN_A);
        String toId = createAccount(IBAN_B);

        // Deposito iniziale: 500 euro
        mockMvc.perform(post("/api/accounts/{id}/deposits", fromId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":500.00}"))
                .andExpect(status().isCreated());

        // Trasferimento di 150 euro
        String response = mockMvc.perform(post("/api/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferJson(fromId, toId, "150.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(150.0))
                .andExpect(jsonPath("$.fromAccountId").value(fromId))
                .andExpect(jsonPath("$.toAccountId").value(toId))
                .andReturn().getResponse().getContentAsString();

        String transferId = JsonPath.read(response, "$.transferId");

        // Controlliamo entrambi i saldi
        mockMvc.perform(get("/api/accounts/{id}", fromId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(350.0));

        mockMvc.perform(get("/api/accounts/{id}", toId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150.0));

        // Controlliamo i due movimenti collegati
        List<AccountTransaction> movements =
                transactionRepository.findByTransferId(
                        UUID.fromString(transferId)
                );

        assertEquals(2, movements.size());

        Set<String> types = movements.stream()
                .map(AccountTransaction::getTransactionType)
                .collect(Collectors.toSet());

        assertEquals(Set.of("TRANSFER_OUT", "TRANSFER_IN"), types);
    }

    @Test
    void transferReturns409WhenInsufficientFunds() throws Exception {

        String fromId = createAccount(IBAN_A);
        String toId = createAccount(IBAN_B);

        mockMvc.perform(post("/api/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferJson(fromId, toId, "100.00")))
                .andExpect(status().isConflict());

        // Nessun saldo deve cambiare
        mockMvc.perform(get("/api/accounts/{id}", fromId))
                .andExpect(jsonPath("$.balance").value(0.0));

        mockMvc.perform(get("/api/accounts/{id}", toId))
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    void transferReturns400WhenSameAccount() throws Exception {

        String accountId = createAccount(IBAN_A);

        mockMvc.perform(post("/api/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferJson(accountId, accountId, "10.00")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferReturns404WhenDestinationMissing() throws Exception {

        String fromId = createAccount(IBAN_A);

        mockMvc.perform(post("/api/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferJson(
                        fromId, UUID.randomUUID().toString(), "10.00"
                )))
                .andExpect(status().isNotFound())
                .andExpect(handler().handlerType(TransferController.class));
    }

    @Test
    void transferReturns400WhenAmountInvalid() throws Exception {

        mockMvc.perform(post("/api/transfers")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferJson(
                        UUID.randomUUID().toString(),
                        UUID.randomUUID().toString(),
                        "-10.00"
                )))
                .andExpect(status().isBadRequest());
    }
    
@Test
void repeatedTransferWithSameKeyExecutesOnlyOnce() throws Exception {

    String fromId = createAccount(IBAN_A);
    String toId = createAccount(IBAN_B);

    // Saldo iniziale: 100 euro
    mockMvc.perform(post("/api/accounts/{id}/deposits", fromId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":100.00}"))
            .andExpect(status().isCreated());

    String key = UUID.randomUUID().toString();
    String payload = transferJson(fromId, toId, "40.00");

    // Primo bonifico
    String firstResponse = mockMvc.perform(post("/api/transfers")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    // Ripetizione identica
    String secondResponse = mockMvc.perform(post("/api/transfers")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(payload))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    String firstId = JsonPath.read(firstResponse, "$.transferId");
    String secondId = JsonPath.read(secondResponse, "$.transferId");

    assertEquals(firstId, secondId);

    // Il saldo deve essere addebitato solo una volta
    mockMvc.perform(get("/api/accounts/{id}", fromId))
            .andExpect(jsonPath("$.balance").value(60.0));

    mockMvc.perform(get("/api/accounts/{id}", toId))
            .andExpect(jsonPath("$.balance").value(40.0));

    assertEquals(2, transactionRepository.findByTransferId(
            UUID.fromString(firstId)
    ).size());
}

@Test
void sameKeyWithDifferentAmountReturns409() throws Exception {

    String fromId = createAccount(IBAN_A);
    String toId = createAccount(IBAN_B);

    mockMvc.perform(post("/api/accounts/{id}/deposits", fromId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":100.00}"))
            .andExpect(status().isCreated());

    String key = UUID.randomUUID().toString();

    // Primo trasferimento di 40 euro
    mockMvc.perform(post("/api/transfers")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(transferJson(fromId, toId, "40.00")))
            .andExpect(status().isCreated());

    // Stessa chiave, ma importo diverso
    mockMvc.perform(post("/api/transfers")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(transferJson(fromId, toId, "30.00")))
            .andExpect(status().isConflict());

    // Nessun secondo addebito
    mockMvc.perform(get("/api/accounts/{id}", fromId))
            .andExpect(jsonPath("$.balance").value(60.0));

    mockMvc.perform(get("/api/accounts/{id}", toId))
            .andExpect(jsonPath("$.balance").value(40.0));
}

@Test
void transferWithoutIdempotencyKeyReturns400() throws Exception {

    mockMvc.perform(post("/api/transfers")
            .contentType(MediaType.APPLICATION_JSON)
            .content(transferJson(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "10.00"
            )))
            .andExpect(status().isBadRequest());
}

}
