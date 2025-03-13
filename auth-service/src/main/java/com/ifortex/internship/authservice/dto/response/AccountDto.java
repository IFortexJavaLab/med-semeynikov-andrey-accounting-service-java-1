package com.ifortex.internship.authservice.dto.response;

import com.ifortex.internship.medstarter.security.model.constant.UserRole;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@Builder
public class AccountDto {
    String accountId;
    String email;
    String phoneNumber;
    String firstName;
    String lastName;
    boolean isTwoFactorEnabled;
    UserRole role;
}
