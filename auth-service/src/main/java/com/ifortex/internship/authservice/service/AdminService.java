package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.dto.request.CreateAdminRequest;
import com.ifortex.internship.authservice.dto.response.AdminDetailsDto;
import com.ifortex.internship.authservice.dto.response.CreateUserResponse;
import com.ifortex.internship.authservice.dto.response.CreatedAccountDto;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.ForbiddenActionException;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.Admin;
import com.ifortex.internship.authservice.model.Role;
import com.ifortex.internship.authservice.model.constant.UserRole;
import com.ifortex.internship.authservice.repository.AdminRepository;
import com.ifortex.internship.authservice.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminRepository adminRepository;
    private final AuthService authService;
    private final RoleRepository roleRepository;
    private final AccountService accountService;

    @Transactional
    public CreateUserResponse createAdmin(CreateAdminRequest request) {

        String email = request.getEmail();
        log.info("Creating admin with email: {}", email);

        AdminDetailsDto adminDetails = authService.getAdminDetailsFromAuthentication();
        boolean isCreatedSuperAdmin = request.isSuper();
        boolean isCreatingSuperAdmin = adminDetails.isSuperAdmin();

        if (isCreatedSuperAdmin && !isCreatingSuperAdmin) {
            log.error("Attempt to create super admin user with role admin by admin with account ID: {}", adminDetails.getAccountId());
            throw new ForbiddenActionException("Only Super Admin can create another Admin.");
        }

        authService.validateEmailNotRegistered(email);

        Role role = roleRepository.findByName(UserRole.ADMIN).orElseThrow(
            () -> {
                log.error("Role with name: {} not found", UserRole.ADMIN);
                return new EntityNotFoundException(
                    String.format("Role with name: %s not found", UserRole.ADMIN));
            });

        CreatedAccountDto accountDto = accountService.createAccount(email, null, role);
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