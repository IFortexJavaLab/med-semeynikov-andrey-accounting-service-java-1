package com.ifortex.internship.authserviceapi.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AccountDto {
    String accountId;
    String email;
    String phoneNumber;
    String firstName;
    String lastName;
    boolean isTwoFactorEnabled;
    String role;
}
