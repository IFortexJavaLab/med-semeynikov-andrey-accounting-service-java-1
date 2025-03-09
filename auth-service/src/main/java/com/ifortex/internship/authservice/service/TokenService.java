package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.dto.response.TokensResponse;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.Admin;
import com.ifortex.internship.authservice.model.Client;
import com.ifortex.internship.authservice.model.RefreshToken;
import com.ifortex.internship.authservice.model.constant.UserRole;
import com.ifortex.internship.authservice.model.stripe.StripeSubscription;
import com.ifortex.internship.authservice.model.stripe.SubscriptionStatus;
import com.ifortex.internship.authservice.repository.AdminRepository;
import com.ifortex.internship.authservice.repository.ClientRepository;
import com.ifortex.internship.medstarter.exception.MedServiceException;
import com.ifortex.internship.medstarter.exception.custom.AuthorizationException;
import com.ifortex.internship.medstarter.exception.custom.EntityNotFoundException;
import com.ifortex.internship.medstarter.exception.custom.UserBlockedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RefreshTokenService refreshTokenService;
    private final ClientRepository clientRepository;
    private final AdminRepository adminRepository;
    @Value("${app.jwtSecret}")
    private final String jwtSecret;

    @Value("${app.jwtExpirationS}")
    private final long jwtExpirationS;

    @Value("${app.refreshTokenExpirationS}")
    private final long refreshTokenExpirationS;

    private static final String HAS_ACTIVE_SUBSCRIPTION_CLAIM = "hasActiveSubscription";
    private static final String IS_SUPER_ADMIN_CLAIM = "isSuperAdmin";
    private static final String ROLE = "ROLE_";
    private static final String CLAIM_ACCOUNT_ID = "accountId";

    public String generateAccessToken(Account account) {

        Map<String, Object> claims = new HashMap<>();
        claims.put(HAS_ACTIVE_SUBSCRIPTION_CLAIM, false);

        var role = account.getRole().getName();
        UUID accountId = account.getAccountId();

        claims.put("role", role);

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
            .signWith(getSigningKey())
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

    public boolean isValid(String authToken) {

        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            log.debug("Access token is valid");
            return true;
        } catch (SignatureException e) {
            log.debug("Invalid JWT signature: {}", e.getMessage());
            throw new AuthorizationException("JWT token is invalid. Please log in again.");
        } catch (MalformedJwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            throw new AuthorizationException("JWT token is malformed. Please log in again.");
        } catch (UnsupportedJwtException e) {
            log.debug("JWT token is unsupported: {}", e.getMessage());
            throw new AuthorizationException("JWT token is unsupported. Please log in again.");
        } catch (IllegalArgumentException e) {
            log.debug("JWT claims string is empty: {}", e.getMessage());
            throw new AuthorizationException("JWT claims string is empty. Please log in again.");
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
        }

        return false;
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public RefreshToken createRefreshToken(String email) {
        return refreshTokenService.createRefreshToken(email);
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    public String getUserIdFromToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get(CLAIM_ACCOUNT_ID, String.class);
    }

    public Boolean hasActiveSubscriptionFromToken(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get(HAS_ACTIVE_SUBSCRIPTION_CLAIM, Boolean.class);
    }

    public Boolean isSuperAdmin(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .get(IS_SUPER_ADMIN_CLAIM, Boolean.class);
    }

    public Optional<LocalDateTime> getSubscriptionEndDateFromToken(String token) {
        Long subscriptionEndDate =
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("subscriptionEndDate", Long.class);
        return Optional.ofNullable(subscriptionEndDate)
            .map(date -> LocalDateTime.ofEpochSecond(date, 0, ZoneOffset.UTC));
    }

    public Collection<SimpleGrantedAuthority> getAuthorityFromToken(String token) {

        log.debug("Getting authorities from access token");

        final Claims claims =
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();

        UserRole role = UserRole.valueOf(claims.get("role", String.class));

        log.debug("Got roles from token: {}", role);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(ROLE + role.name()));

        log.debug("Made authority from roles: {}", authorities);

        return authorities;
    }

}
