package com.ifortex.internship.authserviceapi.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class AuthUserDto {
  private String userId;
  private String email;
  private boolean isTwoFactorEnabled;
  private List<String> roles;
  private boolean isSoftDeleted;
  private String status;
}
