package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.model.Client;
import com.ifortex.internship.authservice.model.Role;
import com.ifortex.internship.authservice.repository.AccountRepository;
import com.ifortex.internship.authservice.repository.ClientRepository;
import com.ifortex.internship.authservice.repository.RoleRepository;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import com.ifortex.internship.medstarter.exception.custom.InternalServiceException;
import com.ifortex.internship.medstarter.model.kafka.KycVerificationEvent;
import com.ifortex.internship.medstarter.model.kafka.constant.KafkaConstants;
import com.ifortex.internship.medstarter.model.kafka.constant.KycEventType;
import com.ifortex.internship.medstarter.security.model.constant.UserRole;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class KycEventListenerService {

    AccountService accountService;
    ClientService clientService;
    ClientRepository clientRepository;
    ParamedicService paramedicService;
    RoleRepository roleRepository;
    AccountRepository accountRepository;

    @KafkaListener(
        topics = KafkaConstants.KYC_VERIFICATION_EVENTS,
        groupId = KafkaConstants.KYC_VERIFICATION_GROUP)
    public void listen(KycVerificationEvent event) {
        log.info("Get message from kyc-verification-events topic with type: {}", event.eventType());

        if (Objects.requireNonNull(event.eventType()) == KycEventType.KYC_VERIFICATION_APPROVED) {
            handleApproval(event);
        } else {
            throw new InternalServiceException("Unknown event type: " + event.eventType());
        }
    }

    private void handleApproval(KycVerificationEvent event) {
        var accountId = event.accountId();
        log.debug("Approving application: {}. Changing role from Client to Paramedic", accountId);

        var account = accountService.findAccountByAccountId(accountId);
        Role role = roleRepository.findByName(UserRole.PARAMEDIC).orElseThrow(
            () -> {
                log.error("Role with name: {} not found", UserRole.PARAMEDIC);
                return new EntityNotFoundException(
                    String.format("Role with name: %s not found", UserRole.PARAMEDIC));
            });
        account.setRole(role);
        accountRepository.save(account);

        log.debug("Deleting client with account: {}", accountId);
        Client clientToDelete = clientService.findClientByAccountId(accountId);
        clientRepository.delete(clientToDelete);
        log.debug("Client with account: {} deleted successfully", accountId);

        log.debug("Creating paramedic with existing account: {}", accountId);
        UUID bonusPolicyId = UUID.randomUUID();
        paramedicService.saveNewParamedic(account, bonusPolicyId);
        log.debug("Paramedic with existing account: {} is created", accountId);

        log.info("Process of approving application for account: {} and changing role is finished", accountId);
    }
}
