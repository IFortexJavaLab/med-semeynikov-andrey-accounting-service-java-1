package com.ifortex.internship.authserviceapi.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UserDto {
  private String email;
  private boolean isTwoFactorEnabled;
  private List<String> roles;
  private boolean isSoftDeleted = false;
  private String status;
  // todo what instead of string in the status and roles?
}
