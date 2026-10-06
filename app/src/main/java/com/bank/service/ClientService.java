package com.bank.service;

import com.bank.dto.*;
import java.util.List;

public interface ClientService {
    ClientDto getClientById(Long id);
    List<AccountDto> getAccountsByClientId(Long clientId);
    AccountDto getAccountById(Long accountId);
    List<AccountDto> getAccountsByAccountId(Long accountId);
}