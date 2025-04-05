package com.ifortex.internship.accountingservice.service;

import com.ifortex.internship.accountingservice.dto.request.CreateParamedicRequest;
import com.ifortex.internship.accountingservice.dto.response.CreateUserResponse;
import com.ifortex.internship.accountingservice.dto.response.CreatedAccountDto;
import com.ifortex.internship.accountingservice.model.Account;
import com.ifortex.internship.accountingservice.model.Paramedic;
import com.ifortex.internship.accountingservice.model.Role;
import com.ifortex.internship.accountingservice.repository.ParamedicRepository;
import com.ifortex.internship.accountingservice.repository.RoleRepository;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import com.ifortex.internship.medstarter.security.model.constant.UserRole;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class ParamedicService {

    ParamedicRepository paramedicRepository;
    RoleRepository roleRepository;
    AccountService accountService;

    @Transactional
    public CreateUserResponse createParamedic(CreateParamedicRequest request) {
        log.info("Creating paramedic with email: {}", request.getEmail());

        CreatedAccountDto accountDto = createAndRegisterParamedic(request.getEmail(), request.getBonusPolicyId());

        String message = String.format(
            "Paramedic with email: %s created successfully",
            accountDto.getAccount().getEmail()
        );

        return new CreateUserResponse(message, accountDto.getPassword(), accountDto.getTempPasswordExpirationHours());
    }

    public void saveNewParamedic(Account account, UUID bonusPolicyId) {
        Paramedic paramedic = new Paramedic()
            .setAccount(account)
            .setBonusPolicyId(bonusPolicyId);
        paramedicRepository.save(paramedic);
    }

    private CreatedAccountDto createAndRegisterParamedic(String email, UUID bonusPolicyId) {

        accountService.validateEmailNotRegistered(email);

        Role role = roleRepository.findByName(UserRole.PARAMEDIC).orElseThrow(
            () -> {
                log.error("Role with name: {} not found", UserRole.PARAMEDIC);
                return new EntityNotFoundException(
                    String.format("Role with name: %s not found", UserRole.PARAMEDIC));
            });

        var accountDto = accountService.createAccount(email, null, role, null);
        var account = accountDto.getAccount();
        saveNewParamedic(account, bonusPolicyId);

        log.info("Paramedic: {} registered successfully", account.getEmail());
        return accountDto;
    }

}
