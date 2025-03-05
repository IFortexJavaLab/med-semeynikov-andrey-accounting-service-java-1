package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.dto.request.CreateClientRequest;
import com.ifortex.internship.authservice.dto.request.RegistrationRequest;
import com.ifortex.internship.authservice.dto.response.CreateUserResponse;
import com.ifortex.internship.authservice.dto.response.CreatedAccountDto;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.AccountRole;
import com.ifortex.internship.authservice.model.Client;
import com.ifortex.internship.authservice.model.constant.RoleType;
import com.ifortex.internship.authservice.repository.AccountRoleRepository;
import com.ifortex.internship.authservice.repository.ClientRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final AccountRoleRepository accountRoleRepository;
    private final AuthService authService;
    private final StripeService stripeService;

    @Transactional
    public CreateUserResponse createClient(CreateClientRequest request) {
        log.info("Creating client with email: {}", request.getEmail());

        CreatedAccountDto accountDto = createAndRegisterClient(request.getEmail(), null);

        String message = String.format(
            "Client with email: %s created successfully",
            accountDto.getAccount().getEmail()
        );

        return new CreateUserResponse(message, accountDto.getPassword(), accountDto.getTempPasswordExpirationHours());
    }

    @Transactional
    public void register(RegistrationRequest request) {
        log.debug("Registering user with email: {}", request.getEmail());
        createAndRegisterClient(request.getEmail(), request.getPassword());
    }

    private CreatedAccountDto createAndRegisterClient(String email, String password) {
        authService.validateEmailNotRegistered(email);

        CreatedAccountDto accountDto = authService.createAccount(email, password);

        Account account = accountDto.getAccount();
        String customerStripeId = stripeService.registerCustomer(account);

        save(account, customerStripeId);

        log.info("User: {} registered successfully", account.getEmail());
        return accountDto;
    }

    private void save(Account account, String customerStripeId) {
        Client client = new Client()
            .setAccount(account)
            .setStripeId(customerStripeId);
        clientRepository.save(client);

        AccountRole accountRole = new AccountRole()
            .setAccount(account)
            .setRoleType(RoleType.CLIENT)
            .setRoleEntityId(client.getId());
        accountRoleRepository.save(accountRole);
    }

}
