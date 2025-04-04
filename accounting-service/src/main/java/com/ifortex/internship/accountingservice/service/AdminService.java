package com.ifortex.internship.accountingservice.service;

import com.ifortex.internship.accountingservice.dto.request.CreateAdminRequest;
import com.ifortex.internship.accountingservice.dto.response.CreateUserResponse;
import com.ifortex.internship.accountingservice.dto.response.CreatedAccountDto;
import com.ifortex.internship.accountingservice.model.Account;
import com.ifortex.internship.accountingservice.model.Admin;
import com.ifortex.internship.accountingservice.model.Role;
import com.ifortex.internship.accountingservice.repository.AdminRepository;
import com.ifortex.internship.accountingservice.repository.RoleRepository;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import com.ifortex.internship.medstarter.exception.custom.ForbiddenActionException;
import com.ifortex.internship.medstarter.security.dto.AdminDetailsDto;
import com.ifortex.internship.medstarter.security.model.constant.UserRole;
import com.ifortex.internship.medstarter.security.service.AuthenticationFacade;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    AdminRepository adminRepository;
    RoleRepository roleRepository;
    AccountService accountService;
    AuthenticationFacade authenticationFacade;

    @Transactional
    public CreateUserResponse createAdmin(CreateAdminRequest request) {

        String email = request.getEmail();
        log.info("Creating admin with email: {}", email);

        AdminDetailsDto adminDetails = authenticationFacade.getAdminDetailsFromAuthentication();
        boolean isCreatedSuperAdmin = request.isSuper();
        boolean isCreatingSuperAdmin = adminDetails.isSuperAdmin();

        if (isCreatedSuperAdmin && !isCreatingSuperAdmin) {
            log.error("Attempt to create super admin user with role admin by admin with account ID: {}", adminDetails.getAccountId());
            throw new ForbiddenActionException("Only Super Admin can create another Admin.");
        }

        accountService.validateEmailNotRegistered(email);

        Role role = roleRepository.findByName(UserRole.ADMIN).orElseThrow(
            () -> {
                log.error("Role with name: {} not found", UserRole.ADMIN);
                return new EntityNotFoundException(
                    String.format("Role with name: %s not found", UserRole.ADMIN));
            });

        CreatedAccountDto accountDto = accountService.createAccount(email, null, role, null);
        Account account = accountDto.getAccount();

        Admin admin = new Admin().setSuperAdmin(isCreatedSuperAdmin).setAccount(account);
        adminRepository.save(admin);

        log.info("Admin: {} with privilege isSuperAdmin = {} created successfully", email, isCreatedSuperAdmin);

        String
            message =
            String.format("Admin: %s with privilege isSuperAdmin = %s created successfully", email, isCreatedSuperAdmin);

        return new CreateUserResponse(message, accountDto.getPassword(), accountDto.getTempPasswordExpirationHours());
    }

}