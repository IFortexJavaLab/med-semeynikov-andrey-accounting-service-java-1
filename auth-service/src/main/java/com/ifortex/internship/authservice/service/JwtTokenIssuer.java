package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.dto.response.TokensResponse;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.Admin;
import com.ifortex.internship.authservice.model.Client;
import com.ifortex.internship.authservice.model.RefreshToken;
import com.ifortex.internship.authservice.model.stripe.StripeSubscription;
import com.ifortex.internship.authservice.model.stripe.SubscriptionStatus;
import com.ifortex.internship.authservice.repository.AdminRepository;
import com.ifortex.internship.authservice.repository.ClientRepository;
import com.ifortex.internship.medstarter.exception.MedServiceException;
import com.ifortex.internship.medstarter.exception.custom.AuthorizationException;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import com.ifortex.internship.medstarter.exception.custom.UserBlockedException;
import com.ifortex.internship.medstarter.security.service.JwtTokenValidator;
import io.jsonwebtoken.Jwts;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.ifortex.internship.medstarter.security.model.constant.JwtConstants.CLAIM_ACCOUNT_ID;
import static com.ifortex.internship.medstarter.security.model.constant.JwtConstants.CLAIM_ROLE;
import static com.ifortex.internship.medstarter.security.model.constant.JwtConstants.HAS_ACTIVE_SUBSCRIPTION_CLAIM;
import static com.ifortex.internship.medstarter.security.model.constant.JwtConstants.IS_SUPER_ADMIN_CLAIM;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtTokenIssuer {

    RefreshTokenService refreshTokenService;
    ClientRepository clientRepository;
    AdminRepository adminRepository;
    JwtTokenValidator jwtTokenValidator;

    @Value("${app.jwtExpirationS}") long jwtExpirationS;
    @Value("${app.refreshTokenExpirationS}") long refreshTokenExpirationS;

    public String generateAccessToken(Account account) {

        Map<String, Object> claims = new HashMap<>();
        claims.put(HAS_ACTIVE_SUBSCRIPTION_CLAIM, false);

        var role = account.getRole().getName();
        UUID accountId = account.getAccountId();

        claims.put(CLAIM_ROLE, role);

        switch (role) {
            case CLIENT:

                Client client = clientRepository.findByAccountId(accountId)
                    .orElseThrow(() -> {
                        log.error("Client for account ID: {} not found", account.getAccountId());
                        return new EntityNotFoundException("Client not found");
                    });

                StripeSubscription activeSubscription =
                    client.getSubscriptions().stream()
                        .filter(subscr -> subscr.getStatus().equals(SubscriptionStatus.ACTIVE))
                        .findFirst()
                        .orElse(null);
                if (activeSubscription != null) {
                    boolean isSubscriptionValid =
                        activeSubscription.getEndDate().isAfter(Instant.now());
                    if (isSubscriptionValid) {
                        claims.put(HAS_ACTIVE_SUBSCRIPTION_CLAIM, true);
                        long expirationDate = activeSubscription.getEndDate().getEpochSecond();
                        claims.put("subscriptionEndDate", expirationDate);
                    }
                }
                break;

            case PARAMEDIC:
                //can be used in the future
                break;

            case ADMIN:
                Admin admin = adminRepository.findByAccountId(accountId)
                    .orElseThrow(() -> {
                        log.error("Admin for account with ID: {} not found", account.getAccountId());
                        return new EntityNotFoundException("Admin not found");
                    });
                claims.put(IS_SUPER_ADMIN_CLAIM, admin.isSuperAdmin());
                break;

            default:
                break;
        }

        claims.put(CLAIM_ACCOUNT_ID, account.getAccountId());
        return Jwts.builder()
            .subject(account.getEmail())
            .claims(claims)
            .issuedAt(new Date(System.currentTimeMillis()))
            .expiration(new Date(System.currentTimeMillis() + Duration.ofSeconds(jwtExpirationS).toMillis()))
            .signWith(jwtTokenValidator.getSigningKey())
            .compact();
    }

    public TokensResponse refreshTokens(String refreshToken) {

        Account account;
        try {
            RefreshToken storedRefreshtoken = refreshTokenService.findByToken(refreshToken);
            refreshTokenService.verifyExpiration(storedRefreshtoken);

            account = storedRefreshtoken.getAccount();

        } catch (MedServiceException e) {
            log.debug("Exception message: {}", e.getMessage());
            throw new AuthorizationException("Your session has expired. Please log in again.");
        }
        boolean isBlocked =
            account.getBlockedUntil() != null && account.getBlockedUntil().isAfter(Instant.now());
        if (isBlocked) {
            log.debug("Account with ID: {} is blocked", account.getAccountId());
            throw new UserBlockedException(
                String.format("Your account is blocked due to: %s", account.getBlockedUntil()));
        }

        String newAccessToken = generateAccessToken(account);
        log.debug("Access token refreshed successfully for account: {}", account.getEmail());

        RefreshToken newRefreshToken = createRefreshToken(account.getEmail());

        return new TokensResponse(
            newAccessToken, newRefreshToken.getToken(), jwtExpirationS, refreshTokenExpirationS);
    }

    public RefreshToken createRefreshToken(String email) {
        return refreshTokenService.createRefreshToken(email);
    }

}
