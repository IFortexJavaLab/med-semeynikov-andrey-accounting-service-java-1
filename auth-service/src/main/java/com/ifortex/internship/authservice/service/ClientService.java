package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.dto.request.CreateClientRequest;
import com.ifortex.internship.authservice.dto.request.RegistrationRequest;
import com.ifortex.internship.authservice.dto.request.SocialUserInfo;
import com.ifortex.internship.authservice.dto.response.CreateUserResponse;
import com.ifortex.internship.authservice.dto.response.CreatedAccountDto;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.Client;
import com.ifortex.internship.authservice.model.Role;
import com.ifortex.internship.authservice.repository.ClientRepository;
import com.ifortex.internship.authservice.repository.RoleRepository;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import com.ifortex.internship.medstarter.security.model.constant.UserRole;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    static final String LOG_USER_REGISTERED_SUCCESSFULLY = "User: {} registered successfully";

    ClientRepository clientRepository;
    StripeService stripeService;
    RoleRepository roleRepository;
    AccountService accountService;

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
        accountService.validateEmailNotRegistered(email);

        Role role = roleRepository.findByName(UserRole.CLIENT).orElseThrow(
            () -> {
                log.error("Role with name: {} not found", UserRole.CLIENT);
                return new EntityNotFoundException(
                    String.format("Role with name: %s not found", UserRole.CLIENT));
            });

        CreatedAccountDto accountDto = accountService.createAccount(email, password, role);

        Account account = accountDto.getAccount();
        String customerStripeId = stripeService.registerCustomer(account);

        save(account, customerStripeId);

        log.info(LOG_USER_REGISTERED_SUCCESSFULLY, account.getEmail());
        return accountDto;
    }

    public Account createAndRegisterSocialClient(SocialUserInfo socialUserInfo) {
        String email = socialUserInfo.getEmail();
        log.info("Registering social user with email: {}", email);

        accountService.validateEmailNotRegistered(email);
        Role role = roleRepository.findByName(UserRole.CLIENT).orElseThrow(
            () -> {
                log.error("Role with name: {} not found", UserRole.CLIENT);
                return new EntityNotFoundException(
                    String.format("Role with name: %s not found", UserRole.CLIENT));
            });

        var account = accountService.createAccountForSocialClient(socialUserInfo, role);

        String customerStripeId = stripeService.registerCustomer(account);
        save(account, customerStripeId);

        log.info(LOG_USER_REGISTERED_SUCCESSFULLY, account.getEmail());
        return account;
    }

    private void save(Account account, String customerStripeId) {
        Client client = new Client()
            .setAccount(account)
            .setStripeId(customerStripeId);
        clientRepository.save(client);
    }

}
