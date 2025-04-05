package com.ifortex.internship.accountingservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifortex.internship.accountingservice.dto.request.SocialUserInfo;
import com.ifortex.internship.accountingservice.dto.response.TokensResponse;
import com.ifortex.internship.accountingservice.model.Account;
import com.ifortex.internship.accountingservice.model.constant.Provider;
import com.ifortex.internship.accountingservice.repository.AccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService implements AuthenticationSuccessHandler {

    AuthService authService;
    ClientService clientService;
    AccountRepository accountRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
        throws IOException {
        log.info("Success response from Social login. Processing");

        SocialUserInfo socialUserInfo = createSocialUserInfoFromAuthentication(authentication);
        String email = socialUserInfo.getEmail();

        log.info("Looking up or registering social user; email: {}; provider: {};", email, socialUserInfo.getProvider());
        TokensResponse tokensResponse;
        Optional<Account> optionalAccount = accountRepository.findByEmail(email);
        Account account = optionalAccount.orElse(null);
        if (optionalAccount.isEmpty()) {
            log.info("Social user with email:{} isn't registered", email);
            account = clientService.createAndRegisterSocialClient(socialUserInfo);
        }
        tokensResponse = authService.authenticateSocialUser(account).getTokens();

        //form a response contained tokens
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        new ObjectMapper().writeValue(response.getWriter(), tokensResponse);

        log.info("Authentication successful for email: {}", email);
    }

    private SocialUserInfo createSocialUserInfoFromAuthentication(Authentication authentication) {
        DefaultOidcUser oidcUser = (DefaultOidcUser) authentication.getPrincipal();
        Map<String, Object> claims = oidcUser.getClaims();
        String email = (String) claims.get("email");
        String firstName = (String) claims.getOrDefault("given_name", null);
        String lastName = (String) claims.getOrDefault("family_name", null);
        Provider provider = Provider.GOOGLE;

        return new SocialUserInfo()
            .setEmail(email)
            .setFirstName(firstName)
            .setLastName(lastName)
            .setProvider(provider);
    }
}
