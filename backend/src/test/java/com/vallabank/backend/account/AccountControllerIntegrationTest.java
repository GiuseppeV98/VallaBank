package com.vallabank.backend.account;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
@SpringBootTest
@AutoConfigureMockMvc
class AccountControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getAccountByIdReturns404WhenMissing() throws Exception {

        UUID missingId = UUID.randomUUID();

        mockMvc.perform(get("/api/accounts/{id}", missingId))
                .andExpect(status().isNotFound());
    }
    @Test
@Transactional
void getAccountByIdReturns200WhenExists() throws Exception {

    // 1. Creiamo un Customer di prova
    String email = "test-" + UUID.randomUUID() + "@example.com";

    String customerJson = """
            {
                "firstName": "Test",
                "lastName": "Account",
                "email": "%s"
            }
            """.formatted(email);

    String customerResponse = mockMvc.perform(
            post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(customerJson)
    )
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String customerId = JsonPath.read(customerResponse, "$.id");


    // 2. Creiamo un Account associato al Customer
    String iban = "IT" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 25)
            .toUpperCase();

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
            .andReturn()
            .getResponse()
            .getContentAsString();

    String accountId = JsonPath.read(accountResponse, "$.id");


    // 3. Recuperiamo il conto e verifichiamo i dati
    mockMvc.perform(get("/api/accounts/{id}", accountId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(accountId))
            .andExpect(jsonPath("$.customerId").value(customerId))
            .andExpect(jsonPath("$.iban").value(iban))
            .andExpect(jsonPath("$.accountType").value("CHECKING"))
            .andExpect(jsonPath("$.currency").value("EUR"))
            .andExpect(jsonPath("$.balance").isNumber())
            .andExpect(jsonPath("$.status").value("ACTIVE"));
}
@Test
@Transactional
void createAccountReturns409WhenIbanAlreadyExists() throws Exception {

    // 1. Creiamo un Customer di prova
    String email = "test-" + UUID.randomUUID() + "@example.com";

    String customerJson = """
            {
                "firstName": "Test",
                "lastName": "Duplicate",
                "email": "%s"
            }
            """.formatted(email);

    String customerResponse = mockMvc.perform(
            post("/api/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(customerJson)
    )
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String customerId = JsonPath.read(customerResponse, "$.id");


    // 2. Prepariamo i dati del conto
    String iban = "IT" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 25)
            .toUpperCase();

    String accountJson = """
            {
                "customerId": "%s",
                "iban": "%s"
            }
            """.formatted(customerId, iban);


    // 3. Creiamo il primo Account
    mockMvc.perform(
            post("/api/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(accountJson)
    )
            .andExpect(status().isCreated());


    // 4. Proviamo a creare nuovamente lo stesso Account
    mockMvc.perform(
            post("/api/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(accountJson)
    )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("IBAN already exists"));
}
@Test
void createAccountReturns404WhenCustomerMissing() throws Exception {

    // 1. Generiamo un UUID di un cliente inesistente
    UUID missingCustomerId = UUID.randomUUID();

    // 2. Generiamo un IBAN di prova casuale
    String iban = "IT" + UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 25)
            .toUpperCase();

    // 3. Prepariamo il JSON della richiesta
    String accountJson = """
            {
                "customerId": "%s",
                "iban": "%s"
            }
            """.formatted(missingCustomerId, iban);

    // 4. Inviamo la POST e verifichiamo la risposta
    mockMvc.perform(
            post("/api/accounts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(accountJson)
    )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("Customer not found"));
}
}