package com.ifortex.internship.authservice.config;

import com.ifortex.internship.authservice.filter.AuthEntryPointJwt;
import com.ifortex.internship.authservice.filter.AuthTokenFilter;
import com.ifortex.internship.authservice.filter.CustomAccessDeniedHandler;
import com.ifortex.internship.authservice.service.CustomAuthenticationProvider;
import com.ifortex.internship.authservice.service.OAuthService;
import com.ifortex.internship.authservice.service.TokenService;
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

    private final TokenService tokenService;
    private final AuthEntryPointJwt unauthorizedHandler;
    private final CustomAccessDeniedHandler accessDeniedHandler;
    private final CustomAuthenticationProvider authenticationProvider;
    private final OAuthService oAuthService;

    @Bean
    public AuthTokenFilter authenticationJwtTokenFilter() {
        return new AuthTokenFilter(tokenService);
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
                        .requestMatchers("/api/v1/subscription/plans").permitAll()
                        .requestMatchers("/api/v1/subscription/**").hasRole("CLIENT")
                        .requestMatchers("/api/v1/subscription/webhooks").permitAll()
                        .requestMatchers("/api/v1/subscription/**").authenticated()
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
