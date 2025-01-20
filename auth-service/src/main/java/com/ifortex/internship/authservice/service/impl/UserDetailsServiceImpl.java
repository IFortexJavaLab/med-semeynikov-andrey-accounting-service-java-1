package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authservice.model.UserDetailsImpl;
import com.ifortex.internship.authservice.model.constant.UserRole;
import com.ifortex.internship.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

  private final UserRepository userRepository;

  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByEmail(username)
            .orElseThrow(
                () ->
                    new UsernameNotFoundException(
                        String.format("User not found with email: %s", username)));

    List<GrantedAuthority> authorities =
        user.getRoles().isEmpty()
            ? List.of(new SimpleGrantedAuthority(UserRole.ROLE_NON_SUBSCRIBED_USER.name()))
            : user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toList());

    return UserDetailsImpl.builder()
        .id(user.getId())
        .email(user.getEmail())
        .password(user.getPassword())
        .isTwoFactorEnabled(user.isTwoFactorEnabled())
        .authorities(authorities)
        .build();
  }
}
