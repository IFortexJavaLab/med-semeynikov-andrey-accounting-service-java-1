package com.ifortex.internship.authservice.model.mapper;

import com.ifortex.internship.authservice.model.User;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

  public AuthUserDto toDto(User user) {
    AuthUserDto dto =
        new AuthUserDto()
            .setUserId(user.getUserId())
            .setEmail(user.getEmail())
            .setTwoFactorEnabled(user.isTwoFactorEnabled())
            .setSoftDeleted(user.isSoftDeleted())
            .setStatus(user.getStatus().name());
    List<String> roles = user.getRoles().stream().map(role -> role.getName().name()).toList();
    dto.setRoles(roles);
    return dto;
  }
}
