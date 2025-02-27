package com.ifortex.internship.authservice.model.mapper;

import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

  /**
   * Converts a {@link User} entity to an {@link AuthUserDto}.
   *
   * <p>This method maps user entity fields to a DTO representation, including user ID, email,
   * two-factor authentication status, soft deletion status, assigned roles, and blocked status.
   *
   * @param user the User entity to be converted
   * @return an AuthUserDto containing the mapped user data
   */
  public AuthUserDto toDto(User user) {
    AuthUserDto dto =
        new AuthUserDto()
            .setUserId(user.getUserId())
            .setEmail(user.getEmail())
            .setTwoFactorEnabled(user.isTwoFactorEnabled())
            .setSoftDeleted(user.isSoftDeleted());

    List<String> roles = user.getRoles().stream().map(role -> role.getName().name()).toList();
    dto.setRoles(roles);

    boolean isBlocked =
        user.getBlockedUntil() != null
            && user.getBlockedUntil().isAfter(LocalDateTime.now(Clock.systemUTC()));
    dto.setBlocked(isBlocked);

    return dto;
  }
}
