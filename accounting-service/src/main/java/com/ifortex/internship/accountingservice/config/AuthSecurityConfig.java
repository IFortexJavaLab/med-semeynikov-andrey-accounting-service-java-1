package com.ifortex.internship.accountingservice.config;

import com.ifortex.internship.accountingservice.service.CustomAuthenticationProvider;
import com.ifortex.internship.accountingservice.service.OAuthService;
import com.ifortex.internship.medstarter.security.filter.AuthEntryPointJwt;
import com.ifortex.internship.medstarter.security.filter.AuthTokenFilter;
import com.ifortex.internship.medstarter.security.filter.CustomAccessDeniedHandler;
import com.ifortex.internship.medstarter.security.service.JwtTokenValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class AuthSecurityConfig {

    private final JwtTokenValidator jwtTokenValidator;
    private final AuthEntryPointJwt unauthorizedHandler;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CustomAuthenticationProvider authenticationProvider;
    private final OAuthService oAuthService;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter(jwtTokenValidator);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(
                session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(
                auth ->
                    auth.requestMatchers(
                            "/api/v1/account/password/reset",
                            "/api/v1/account/password/reset-confirm").permitAll()
                        .requestMatchers("/api/v1/account/**").authenticated()
                        .requestMatchers("/api/v1/auth/logout").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/accounting/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/subscription/webhooks").permitAll()
                        .requestMatchers("/api/v1/subscription/plans").hasAnyRole("CLIENT", "ADMIN")
                        .requestMatchers("/api/v1/subscription/**").hasRole("CLIENT")
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs*/**").permitAll()
                        .requestMatchers("/success.html", "/cancel.html").permitAll()
                        .anyRequest().authenticated())
            .exceptionHandling(
                exception ->
                    exception
                        .authenticationEntryPoint(unauthorizedHandler)
                        .accessDeniedHandler(accessDeniedHandler))
            .oauth2Login(config -> config.successHandler(oAuthService));

        http.authenticationProvider(authenticationProvider);
        http.addFilterBefore(authenticationJwtTokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
        throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
