package com.ifortex.internship.authservice.model.mapper;

import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

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
