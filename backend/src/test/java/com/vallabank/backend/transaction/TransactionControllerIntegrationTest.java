
package com.vallabank.backend.transaction;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountTransactionRepository transactionRepository;

    @Test
    void depositReturns201AndUpdatesBalance() throws Exception {

        // 1. Creiamo un cliente
        String customerJson = """
                {
                    "firstName": "Deposit",
                    "lastName": "Test",
                    "email": "deposit-%s@example.com"
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

        // 2. Creiamo un conto
        String accountJson = """
                {
                    "customerId": "%s",
                    "iban": "IT37X0542811101000000000001"
                }
                """.formatted(customerId);

        String accountResponse = mockMvc.perform(
                post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson)
        )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String accountId = JsonPath.read(accountResponse, "$.id");

        // 3. Effettuiamo il deposito
        String depositResponse = mockMvc.perform(
                post("/api/accounts/{id}/deposits", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}")
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(100.0))
                .andExpect(jsonPath("$.balanceAfter").value(100.0))
                .andReturn().getResponse().getContentAsString();

        // 4. Verifichiamo il saldo del conto
        mockMvc.perform(get("/api/accounts/{id}", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.0));

        // 5. Verifichiamo che il movimento sia stato registrato
        String transactionId = JsonPath.read(
                depositResponse, "$.transactionId"
        );

        assertTrue(transactionRepository.findById(
                UUID.fromString(transactionId)
        ).isPresent());
    }

    @Test
    void depositReturns400WhenAmountInvalid() throws Exception {

        mockMvc.perform(
                post("/api/accounts/{id}/deposits", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-10.00}")
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void depositReturns404WhenAccountMissing() throws Exception {

        mockMvc.perform(
                post("/api/accounts/{id}/deposits", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}")
        )
                .andExpect(status().isNotFound());
    }
}
