package com.ifortex.internship.accountingservice.unit.service;

import com.ifortex.internship.accountingservice.dto.request.CreateClientRequest;
import com.ifortex.internship.accountingservice.dto.request.RegistrationRequest;
import com.ifortex.internship.accountingservice.dto.request.SocialUserInfo;
import com.ifortex.internship.accountingservice.dto.response.CreateUserResponse;
import com.ifortex.internship.accountingservice.dto.response.CreatedAccountDto;
import com.ifortex.internship.accountingservice.model.Account;
import com.ifortex.internship.accountingservice.model.Client;
import com.ifortex.internship.accountingservice.model.Role;
import com.ifortex.internship.accountingservice.model.constant.Provider;
import com.ifortex.internship.accountingservice.repository.ClientRepository;
import com.ifortex.internship.accountingservice.repository.RoleRepository;
import com.ifortex.internship.accountingservice.service.AccountService;
import com.ifortex.internship.accountingservice.service.ClientService;
import com.ifortex.internship.accountingservice.service.StripeService;
import com.ifortex.internship.medstarter.exception.custom.DuplicateResourceException;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import com.ifortex.internship.medstarter.security.model.constant.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock ClientRepository clientRepository;
    @Mock StripeService stripeService;
    @Mock RoleRepository roleRepository;
    @Mock AccountService accountService;

    @InjectMocks ClientService clientService;

    @Test
    void createClient_shouldReturnResponse_whenSuccess() {
        String email = "test@example.com";
        String firstName = "John";
        CreateClientRequest request = new CreateClientRequest(firstName, email);
        Account account = new Account().setEmail(email);
        CreatedAccountDto dto = new CreatedAccountDto(account, "tempPass123!", 24);

        when(roleRepository.findByName(UserRole.CLIENT)).thenReturn(Optional.of(new Role()));
        when(accountService.createAccount(eq(email), isNull(), any(), eq(firstName))).thenReturn(dto);
        when(stripeService.registerCustomer(account)).thenReturn("stripe_123");

        CreateUserResponse response = clientService.createClient(request);

        assertEquals("Client with email: test@example.com created successfully", response.getMessage());
        assertEquals("tempPass123!", response.getTempPassword());
        assertEquals(24, response.getPasswordExpirationH());
    }

    @Test
    void register_shouldSucceed_whenAllValid() {
        RegistrationRequest request = new RegistrationRequest(
            "Anna",
            "anna@example.com",
            "Password123!",
            "Password123!"
        );

        Role role = new Role();
        Account account = new Account().setEmail(request.email());
        CreatedAccountDto dto = new CreatedAccountDto(account, request.password(), 12);

        when(roleRepository.findByName(UserRole.CLIENT)).thenReturn(Optional.of(role));
        when(accountService.createAccount(eq(request.email()), eq(request.password()), eq(role), eq(request.firstName())))
            .thenReturn(dto);
        when(stripeService.registerCustomer(account)).thenReturn("stripe_id");

        assertDoesNotThrow(() -> clientService.register(request));
    }

    @Test
    void createClient_shouldThrow_whenEmailAlreadyRegistered() {
        String email = "existing@example.com";
        CreateClientRequest request = new CreateClientRequest("Bob", email);

        doThrow(new DuplicateResourceException("Email: existing@example.com is already registered."))
            .when(accountService).validateEmailNotRegistered(email);

        DuplicateResourceException ex = assertThrows(DuplicateResourceException.class,
            () -> clientService.createClient(request));
        assertEquals("Email: existing@example.com is already registered.", ex.getMessage());
    }

    @Test
    void register_shouldThrow_whenRoleNotFound() {
        RegistrationRequest request = new RegistrationRequest(
            "Eva",
            "eva@example.com",
            "Pass123@!",
            "Pass123@!"
        );
        when(roleRepository.findByName(UserRole.CLIENT)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class, () -> clientService.register(request));
        assertTrue(ex.getMessage().contains("Role with name: CLIENT not found"));
    }

    @Test
    void createAndRegisterSocialClient_shouldReturnAccount_whenValid() {
        SocialUserInfo socialUser = new SocialUserInfo();
        socialUser.setEmail("social@example.com");
        socialUser.setFirstName("Leo");
        socialUser.setProvider(Provider.GOOGLE);

        Account account = new Account().setEmail(socialUser.getEmail());
        Role role = new Role();

        when(roleRepository.findByName(UserRole.CLIENT)).thenReturn(Optional.of(role));
        when(accountService.createAccountForSocialClient(socialUser, role)).thenReturn(account);
        when(stripeService.registerCustomer(account)).thenReturn("stripe_id");

        Account result = clientService.createAndRegisterSocialClient(socialUser);

        assertEquals("social@example.com", result.getEmail());
        verify(clientRepository).save(any(Client.class));
    }

    @Test
    void findClientByAccountId_shouldReturnClient_whenFound() {
        UUID accountId = UUID.randomUUID();
        Client client = new Client();
        when(clientRepository.findByAccountId(accountId)).thenReturn(Optional.of(client));

        Client result = clientService.findClientByAccountId(accountId);
        assertSame(client, result);
    }

    @Test
    void findClientByAccountId_shouldThrow_whenNotFound() {
        UUID accountId = UUID.randomUUID();
        when(clientRepository.findByAccountId(accountId)).thenReturn(Optional.empty());

        EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
            () -> clientService.findClientByAccountId(accountId));
        assertTrue(ex.getMessage().contains("Client with account"));
    }
}
