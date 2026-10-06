package com.bank.controller;

import com.bank.dto.*;
import com.bank.service.ClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BankController.class)
class BankControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientService clientService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void me_Success() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", bearer(jwt("ivanov", "1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("ivanov"))
                .andExpect(jsonPath("$.clientId").value(1));
    }

    @Test
    void me_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_MalformedToken() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_WrongScheme() throws Exception {
        mockMvc.perform(get("/api/me").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getClient_Success() throws Exception {
        ClientDto client = new ClientDto(1L, "Иван", "Иванов", "Иванович", "+79001234567", "ivanov@mail.ru");

        when(clientService.getClientById(1L)).thenReturn(client);

        mockMvc.perform(get("/api/client/1").header("Authorization", bearer(jwt("ivanov", "1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Иван"))
                .andExpect(jsonPath("$.lastName").value("Иванов"));
    }

    @Test
    void getClient_NotFound() throws Exception {
        when(clientService.getClientById(999L))
                .thenThrow(new IllegalArgumentException("Client not found"));

        mockMvc.perform(get("/api/client/999").header("Authorization", bearer(jwt("ivanov", "1"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getClient_WithoutToken() throws Exception {
        mockMvc.perform(get("/api/client/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAccount_Success() throws Exception {
        AccountDto account = new AccountDto(1L, "40817810000000000001", "ДЕПОЗИТ",
                new BigDecimal("10000.00"), "RUB", LocalDateTime.now(), 1L);

        when(clientService.getAccountsByAccountId(1L)).thenReturn(List.of(account));

        mockMvc.perform(get("/api/account/1").header("Authorization", bearer(jwt("ivanov", "1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value("40817810000000000001"));
    }

    @Test
    void getClientAccounts_Success() throws Exception {
        AccountDto account = new AccountDto(1L, "40817810000000000001", "ДЕПОЗИТ",
                new BigDecimal("10000.00"), "RUB", LocalDateTime.now(), 1L);

        when(clientService.getAccountsByClientId(1L)).thenReturn(List.of(account));

        mockMvc.perform(get("/api/client/1/accounts").header("Authorization", bearer(jwt("ivanov", "1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountNumber").value("40817810000000000001"));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String jwt(String username, String clientId) throws Exception {
        Map<String, Object> header = Map.of("alg", "RS256", "typ", "JWT");
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", "http://localhost:8081/realms/bank");
        payload.put("sub", "uuid-" + username);
        payload.put("preferred_username", username);
        if (clientId != null) {
            payload.put("client_id", clientId);
        }
        return base64Url(objectMapper.writeValueAsBytes(header))
                + "." + base64Url(objectMapper.writeValueAsBytes(payload))
                + ".fake-signature";
    }

    private String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}