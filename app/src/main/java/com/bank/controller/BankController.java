package com.bank.controller;

import com.bank.dto.*;
import com.bank.security.JwtAuthInterceptor;
import com.bank.service.ClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "Bank API", description = "API for bank operations (requires Bearer JWT from Keycloak)")
public class BankController {

    private final ClientService clientService;

    public BankController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns the user identity extracted from the JWT bearer token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User identity",
                    content = @Content(schema = @Schema(implementation = MeDto.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token")
    })
    public ResponseEntity<MeDto> me(HttpServletRequest request) {
        String username = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USERNAME);
        String clientId = (String) request.getAttribute(JwtAuthInterceptor.ATTR_CLIENT_ID);
        Long id = clientId != null ? Long.parseLong(clientId) : null;
        return ResponseEntity.ok(new MeDto(username, id));
    }

    @GetMapping("/client/{id}")
    @Operation(summary = "Get client", description = "Get client information by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Client found",
                    content = @Content(schema = @Schema(implementation = ClientDto.class))),
            @ApiResponse(responseCode = "404", description = "Client not found")
    })
    public ResponseEntity<ClientDto> getClient(
            @Parameter(description = "Client ID") @PathVariable Long id) {
        try {
            ClientDto client = clientService.getClientById(id);
            return ResponseEntity.ok(client);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/account/{id}")
    @Operation(summary = "Get account", description = "Get account information by ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Account found",
                    content = @Content(schema = @Schema(implementation = AccountDto.class))),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public ResponseEntity<List<AccountDto>> getAccount(
            @Parameter(description = "Account ID") @PathVariable Long id) {
        try {
            List<AccountDto> accounts = clientService.getAccountsByAccountId(id);
            return ResponseEntity.ok(accounts);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/client/{clientId}/accounts")
    @Operation(summary = "Get client accounts", description = "Get all accounts for a client")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Accounts found",
                    content = @Content(schema = @Schema(implementation = AccountDto.class))),
            @ApiResponse(responseCode = "404", description = "Client not found")
    })
    public ResponseEntity<List<AccountDto>> getClientAccounts(
            @Parameter(description = "Client ID") @PathVariable Long clientId) {
        List<AccountDto> accounts = clientService.getAccountsByClientId(clientId);
        if (accounts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(accounts);
    }
}