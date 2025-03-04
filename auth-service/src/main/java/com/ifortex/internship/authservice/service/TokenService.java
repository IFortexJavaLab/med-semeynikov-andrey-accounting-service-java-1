package com.ifortex.internship.authservice.service;

import com.ifortex.internship.authservice.exception.AuthServiceException;
import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.exception.custom.EntityNotFoundException;
import com.ifortex.internship.authservice.exception.custom.UserBlockedException;
import com.ifortex.internship.authservice.model.Account;
import com.ifortex.internship.authservice.model.AccountRole;
import com.ifortex.internship.authservice.model.Admin;
import com.ifortex.internship.authservice.model.Client;
import com.ifortex.internship.authservice.model.RefreshToken;
import com.ifortex.internship.authservice.model.constant.RoleType;
import com.ifortex.internship.authservice.repository.AccountRoleRepository;
import com.ifortex.internship.authservice.repository.AdminRepository;
import com.ifortex.internship.authservice.repository.ClientRepository;
import com.ifortex.internship.authservice.stripe.model.StripeSubscription;
import com.ifortex.internship.authservice.stripe.model.SubscriptionStatus;
import com.ifortex.internship.authserviceapi.dto.response.TokensResponse;
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
import org.springframework.security.core.GrantedAuthority;
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
import javax.crypto.SecretKey;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RefreshTokenService refreshTokenService;
    private final AccountRoleRepository accountRoleRepository;
    private final ClientRepository clientRepository;
    private final AdminRepository adminRepository;
    @Value("${app.jwtSecret}")
    private String jwtSecret;

    @Value("${app.jwtExpirationS}")
    private long jwtExpirationS;

    @Value("${app.refreshTokenExpirationS}")
    private long refreshTokenExpirationS;

    private static final String LOG_ADMIN_NOT_FOUND = "Admin with local ID: {} for account with ID: {} not found";
    private static final String LOG_CLIENT_NOT_FOUND = "Client with local ID: {} for account with ID: {} not found";
    private static final String HAS_ACTIVE_SUBSCRIPTION_CLAIM = "hasActiveSubscription";
    private static final String IS_SUPER_ADMIN_CLAIM = "isSuperAdmin";
    private static final String ROLE = "ROLE_";

    public String generateAccessToken(Account account) {

        AccountRole accountRole = accountRoleRepository.findByAccount(account)
            .orElseThrow(() -> {
                log.error("Account role not found for account: {}", account.getAccountId());
                return new EntityNotFoundException("Role not found");
            });

        Map<String, Object> claims = new HashMap<>();
        claims.put(HAS_ACTIVE_SUBSCRIPTION_CLAIM, false);

        RoleType roleType = accountRole.getRoleType();
        claims.put("role", roleType);

        switch (roleType) {
            case CLIENT:

                Client client = clientRepository.findById(accountRole.getRoleEntityId())
                    .orElseThrow(() -> {
                        log.error(LOG_CLIENT_NOT_FOUND, accountRole.getRoleEntityId(), account.getAccountId());
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
                Admin admin = adminRepository.findById(accountRole.getRoleEntityId())
                    .orElseThrow(() -> {
                        log.error(LOG_ADMIN_NOT_FOUND, accountRole.getRoleEntityId(), account.getAccountId());
                        return new EntityNotFoundException("Admin not found");
                    });
                claims.put(IS_SUPER_ADMIN_CLAIM, admin.isSuperAdmin());
                break;
        }

        //

        claims.put("accountId", account.getAccountId());
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

        } catch (AuthServiceException e) {
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

    public boolean isExpired(String authToken) {

        try {
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(authToken);
            return false;
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
            return true;
        } catch (Exception e) {
            return false;
        }
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
            .get("accountId", String.class); // todo move to constants
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

    public Collection<? extends GrantedAuthority> getAuthorityFromToken(String token) {

        log.debug("Getting authorities from access token");

        final Claims claims =
            Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();

        RoleType role = RoleType.valueOf(claims.get("role", String.class));

        log.debug("Got roles from token: {}", role);

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(ROLE + role.name()));

        log.debug("Made authority from roles: {}", authorities);

        return authorities;
    }

}
