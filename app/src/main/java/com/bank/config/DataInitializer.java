package com.bank.config;

import com.bank.model.Client;
import com.bank.model.Account;
import com.bank.repository.ClientRepository;
import com.bank.repository.AccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(ClientRepository clientRepository, AccountRepository accountRepository) {
        return args -> {
            if (clientRepository.count() == 0) {
                Client client1 = new Client(1L, "ivanov", "password123", "Иван", "Иванов", "Иванович", "+79001234567", "ivanov@mail.ru", null);
                Client client2 = new Client(2L, "petrova", "password456", "Анна", "Петрова", "Сергеевна", "+79009876543", "petrova@mail.ru", null);

                client1 = clientRepository.save(client1);
                client2 = clientRepository.save(client2);

                Account acc1 = new Account(1L, "40817810000000000001", "ДЕПОЗИТ", new BigDecimal("10000.00"), "RUB", LocalDateTime.now(), client1);
                Account acc2 = new Account(2L, "40817810000000000002", "ТЕКУЩИЙ", new BigDecimal("5000.00"), "USD", LocalDateTime.now(), client1);
                Account acc3 = new Account(3L, "40817810000000000003", "ДЕПОЗИТ", new BigDecimal("25000.00"), "RUB", LocalDateTime.now(), client2);

                accountRepository.saveAll(List.of(acc1, acc2, acc3));
            }
        };
    }
}