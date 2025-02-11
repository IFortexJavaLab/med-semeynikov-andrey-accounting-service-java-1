package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.model.TemporaryPassword;
import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.model.constant.UserRole;
import com.ifortex.internship.authservice.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CustomAuthenticationProvider implements AuthenticationProvider {

  private final UserRepository userRepository;

  private final PasswordEncoder passwordEncoder;

  public CustomAuthenticationProvider(
      UserRepository userRepository, @Lazy PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    String email = (String) authentication.getPrincipal();
    String password = (String) authentication.getCredentials();

    User user = findUserByEmail(email);

    TemporaryPassword temporaryPassword = user.getTemporaryPassword();
    if (temporaryPassword != null) {
      boolean isTemporaryPasswordExpired =
          temporaryPassword.getExpirationDate().isBefore(LocalDateTime.now());
      if (isTemporaryPasswordExpired) {
        log.debug("Temporary password has expired for user: {}", email);
        throw new AuthorizationException("Invalid email or password");
      }

      if (verifyPassword(password, temporaryPassword.getTemporaryPasswordHash())) {
        return createAuthenticationToken(user, password);
      } else {
        log.debug("Invalid temporary password for user: {}", email);
        throw new AuthorizationException("Invalid email or password");
      }
    }

    if (user.getPassword() == null) {
      log.debug("No main password for user: {}", email);
      throw new AuthorizationException("Invalid email or password");
    }

    if (verifyPassword(password, user.getPassword())) {
      return createAuthenticationToken(user, password);
    } else {
      throw new AuthorizationException("Invalid email or password");
    }
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }

  private User findUserByEmail(String email) {
    return userRepository
        .findByEmail(email)
        .orElseThrow(
            () -> {
              log.debug("User with email: {} not found", email);
              return new UsernameNotFoundException("User not found");
            });
  }

  private boolean verifyPassword(String inputPassword, String storedPasswordHash) {
    return passwordEncoder.matches(inputPassword, storedPasswordHash);
  }

  private Authentication createAuthenticationToken(User user, String password) {
    List<GrantedAuthority> authorities =
        user.getRoles().isEmpty()
            ? List.of(new SimpleGrantedAuthority(UserRole.ROLE_NON_SUBSCRIBED_USER.name()))
            : user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());

    return new UsernamePasswordAuthenticationToken(user, password, authorities);
  }
}
