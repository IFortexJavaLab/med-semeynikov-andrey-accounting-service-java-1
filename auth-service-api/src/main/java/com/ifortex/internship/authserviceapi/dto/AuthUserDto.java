package com.ifortex.internship.authserviceapi.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class AuthUserDto {
    String userId;
    String email;
    boolean isTwoFactorEnabled;
    List<String> roles;
    boolean isSoftDeleted;
    boolean isBlocked;
}
