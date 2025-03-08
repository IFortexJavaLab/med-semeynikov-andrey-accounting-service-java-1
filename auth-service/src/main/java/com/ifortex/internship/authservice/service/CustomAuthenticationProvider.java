package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.UserBlockedException;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.TemporaryPassword;
import com.ifortex.internship.authservice.repository.AccountRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    static final String LOG_INVALID_EMAIL_OR_PASSWORD = "Invalid email or password";
    static final String ROLE = "ROLE_";

    PasswordEncoder passwordEncoder;
    AccountRepository accountRepository;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String email = (String) authentication.getPrincipal();
        String password = (String) authentication.getCredentials();

        Account account = accountRepository
            .findByEmail(email)
            .orElseThrow(
                () -> {
                    log.debug("Account with email: {} not found", email);
                    return new UsernameNotFoundException("User not found");
                });

        boolean isSoftDeleted = account.isSoftDeleted();
        if (isSoftDeleted) {
            log.debug("Account with ID: {} is soft deleted", account.getAccountId());
            throw new AuthorizationException(LOG_INVALID_EMAIL_OR_PASSWORD);
        }

        TemporaryPassword temporaryPassword = account.getTemporaryPassword();
        if (temporaryPassword != null) {
            boolean isTemporaryPasswordExpired =
                temporaryPassword.getExpirationDate().isBefore(Instant.now());
            if (isTemporaryPasswordExpired) {
                log.debug("Temporary password has expired for account: {}", email);
                throw new AuthorizationException(LOG_INVALID_EMAIL_OR_PASSWORD);
            }

            if (verifyPassword(password, temporaryPassword.getTemporaryPasswordHash())) {
                return createAuthenticationToken(account, password);
            } else {
                log.debug("Invalid temporary password for account: {}", email);
                throw new AuthorizationException(LOG_INVALID_EMAIL_OR_PASSWORD);
            }
        }

        if (account.getPasswordHash() == null) {
            log.debug("No main password for account: {}", email);
            throw new AuthorizationException(LOG_INVALID_EMAIL_OR_PASSWORD);
        }

        if (!verifyPassword(password, account.getPasswordHash())) {
            throw new AuthorizationException(LOG_INVALID_EMAIL_OR_PASSWORD);
        }

        boolean isBlocked =
            account.getBlockedUntil() != null && account.getBlockedUntil().isAfter(Instant.now());
        if (isBlocked) {
            log.debug("Account with ID: {} is blocked", account.getAccountId());
            throw new UserBlockedException(
                String.format("Your account is blocked due to: %s", account.getBlockedUntil()));
        }

        return createAuthenticationToken(account, password);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private boolean verifyPassword(String inputPassword, String storedPasswordHash) {
        return passwordEncoder.matches(inputPassword, storedPasswordHash);
    }

    private Authentication createAuthenticationToken(Account account, String password) {

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(ROLE + account.getRole().getName().name()));

        return new UsernamePasswordAuthenticationToken(account, password, authorities);
    }
}
