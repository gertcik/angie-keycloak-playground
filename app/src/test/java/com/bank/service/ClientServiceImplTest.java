package com.bank.service;

import com.bank.dto.*;
import com.bank.model.Client;
import com.bank.model.Account;
import com.bank.repository.ClientRepository;
import com.bank.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientServiceImplTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private ClientServiceImpl clientService;

    private Client testClient;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        testClient = new Client();
        testClient.setId(1L);
        testClient.setLogin("ivanov");
        testClient.setPassword("password123");
        testClient.setFirstName("Иван");
        testClient.setLastName("Иванов");
        testClient.setMiddleName("Иванович");
        testClient.setPhone("+79001234567");
        testClient.setEmail("ivanov@mail.ru");

        testAccount = new Account();
        testAccount.setId(1L);
        testAccount.setAccountNumber("40817810000000000001");
        testAccount.setAccountType("ДЕПОЗИТ");
        testAccount.setBalance(new BigDecimal("10000.00"));
        testAccount.setCurrency("RUB");
        testAccount.setCreatedAt(LocalDateTime.now());
        testAccount.setClient(testClient);
    }

    @Test
    void getClientById_Success() {
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));

        ClientDto result = clientService.getClientById(1L);

        assertNotNull(result);
        assertEquals("Иван", result.getFirstName());
        assertEquals("Иванов", result.getLastName());
    }

    @Test
    void getClientById_NotFound() {
        when(clientRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            clientService.getClientById(999L);
        });
    }

    @Test
    void getAccountsByClientId_Success() {
        when(accountRepository.findByClientId(1L)).thenReturn(List.of(testAccount));

        List<AccountDto> accounts = clientService.getAccountsByClientId(1L);

        assertNotNull(accounts);
        assertEquals(1, accounts.size());
        assertEquals("40817810000000000001", accounts.get(0).getAccountNumber());
    }

    @Test
    void getAccountById_Success() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        AccountDto result = clientService.getAccountById(1L);

        assertNotNull(result);
        assertEquals("40817810000000000001", result.getAccountNumber());
    }

    @Test
    void getAccountById_NotFound() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            clientService.getAccountById(999L);
        });
    }

    @Test
    void getAccountsByAccountId_Success() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(accountRepository.findByClientId(1L)).thenReturn(List.of(testAccount));

        List<AccountDto> accounts = clientService.getAccountsByAccountId(1L);

        assertNotNull(accounts);
        assertEquals(1, accounts.size());
    }
}