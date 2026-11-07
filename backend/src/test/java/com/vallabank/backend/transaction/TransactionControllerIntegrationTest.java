package com.vallabank.backend.transaction;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "CUSTOMER")
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
                post("/api/customers").with(csrf())
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
                post("/api/accounts").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson)
        )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String accountId = JsonPath.read(accountResponse, "$.id");

        // 3. Effettuiamo il deposito
        String depositResponse = mockMvc.perform(
                post("/api/accounts/{id}/deposits", accountId).with(csrf())
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
                post("/api/accounts/{id}/deposits", UUID.randomUUID()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-10.00}")
        )
                .andExpect(status().isBadRequest());
    }

    @Test
    void depositReturns404WhenAccountMissing() throws Exception {

        mockMvc.perform(
                post("/api/accounts/{id}/deposits", UUID.randomUUID()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}")
        )
                .andExpect(status().isNotFound());
    }

@Test
void withdrawalReturns201AndUpdatesBalance() throws Exception {

    String accountId = createAccountForWithdrawal();

    // Depositiamo 100 euro
    mockMvc.perform(post("/api/accounts/{id}/deposits", accountId).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":100.00}"))
            .andExpect(status().isCreated());

    // Preleviamo 40 euro
    String response = mockMvc.perform(
            post("/api/accounts/{id}/withdrawals", accountId).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":40.00}")
    )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
            .andExpect(jsonPath("$.amount").value(40.0))
            .andExpect(jsonPath("$.balanceAfter").value(60.0))
            .andReturn().getResponse().getContentAsString();

    // Controlliamo il saldo
    mockMvc.perform(get("/api/accounts/{id}", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(60.0));

    // Controlliamo il movimento registrato
    String transactionId = JsonPath.read(response, "$.transactionId");

    assertTrue(transactionRepository.findById(
            UUID.fromString(transactionId)
    ).isPresent());
}

@Test
void withdrawalReturns409WhenInsufficientFunds() throws Exception {

    String accountId = createAccountForWithdrawal();

    mockMvc.perform(post("/api/accounts/{id}/withdrawals", accountId).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":50.00}"))
            .andExpect(status().isConflict());

    // Il saldo deve essere rimasto invariato
    mockMvc.perform(get("/api/accounts/{id}", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(0.0));
}

@Test
void withdrawalReturns400WhenAmountInvalid() throws Exception {

    mockMvc.perform(post("/api/accounts/{id}/withdrawals", UUID.randomUUID()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":-10.00}"))
            .andExpect(status().isBadRequest());
}

@Test
void withdrawalReturns404WhenAccountMissing() throws Exception {

    mockMvc.perform(post("/api/accounts/{id}/withdrawals", UUID.randomUUID()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":20.00}"))
            .andExpect(status().isNotFound());
}

private String createAccountForWithdrawal() throws Exception {

    String customerJson = """
            {
                "firstName": "Withdrawal",
                "lastName": "Test",
                "email": "withdrawal-%s@example.com"
            }
            """.formatted(UUID.randomUUID());

    String customerResponse = mockMvc.perform(
            post("/api/customers").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(customerJson)
    )
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    String customerId = JsonPath.read(customerResponse, "$.id");

    String accountJson = """
            {
                "customerId": "%s",
                "iban": "IT37X0542811101000000000001"
            }
            """.formatted(customerId);

    String accountResponse = mockMvc.perform(
            post("/api/accounts").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(accountJson)
    )
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

    return JsonPath.read(accountResponse, "$.id");
}

@Test
void getTransactionsReturnsDepositAndWithdrawal() throws Exception {

    String accountId = createAccountForWithdrawal();

    // Deposito iniziale di 100 euro
    mockMvc.perform(post("/api/accounts/{id}/deposits", accountId).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":100.00}"))
            .andExpect(status().isCreated());

    // Prelievo di 40 euro
    mockMvc.perform(post("/api/accounts/{id}/withdrawals", accountId).with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":40.00}"))
            .andExpect(status().isCreated());

    // Recuperiamo lo storico
    mockMvc.perform(get("/api/accounts/{id}/transactions", accountId)
            .param("page", "0")
            .param("size", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(1))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.transactions[0].type").value("WITHDRAWAL"))
            .andExpect(jsonPath("$.transactions[0].balanceAfter").value(60.0))
            .andExpect(jsonPath("$.transactions[1].type").value("DEPOSIT"))
            .andExpect(jsonPath("$.transactions[1].balanceAfter").value(100.0));
}

@Test
void getTransactionsReturns404WhenAccountMissing() throws Exception {

    mockMvc.perform(get("/api/accounts/{id}/transactions", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(org.springframework.test.web.servlet.result
                    .MockMvcResultMatchers.handler()
                    .handlerType(TransactionController.class));
}

@Test
void getTransactionsReturns400WhenPaginationInvalid() throws Exception {

    mockMvc.perform(get("/api/accounts/{id}/transactions", UUID.randomUUID())
            .param("page", "-1")
            .param("size", "20"))
            .andExpect(status().isBadRequest());

    mockMvc.perform(get("/api/accounts/{id}/transactions", UUID.randomUUID())
            .param("page", "0")
            .param("size", "101"))
            .andExpect(status().isBadRequest());
}

}
