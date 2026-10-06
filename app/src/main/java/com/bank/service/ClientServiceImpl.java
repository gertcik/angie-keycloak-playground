package com.bank.service;

import com.bank.dto.*;
import com.bank.model.Client;
import com.bank.model.Account;
import com.bank.repository.ClientRepository;
import com.bank.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;

    public ClientServiceImpl(ClientRepository clientRepository, AccountRepository accountRepository) {
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
    }

    @Override
    public ClientDto getClientById(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));
        return toClientDto(client);
    }

    @Override
    public List<AccountDto> getAccountsByClientId(Long clientId) {
        List<Account> accounts = accountRepository.findByClientId(clientId);
        return accounts.stream().map(this::toAccountDto).collect(Collectors.toList());
    }

    @Override
    public AccountDto getAccountById(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return toAccountDto(account);
    }

    @Override
    public List<AccountDto> getAccountsByAccountId(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        return accountRepository.findByClientId(account.getClient().getId())
                .stream().map(this::toAccountDto).collect(Collectors.toList());
    }

    private ClientDto toClientDto(Client client) {
        return new ClientDto(
                client.getId(),
                client.getFirstName(),
                client.getLastName(),
                client.getMiddleName(),
                client.getPhone(),
                client.getEmail()
        );
    }

    private AccountDto toAccountDto(Account account) {
        return new AccountDto(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountType(),
                account.getBalance(),
                account.getCurrency(),
                account.getCreatedAt(),
                account.getClient().getId()
        );
    }
}