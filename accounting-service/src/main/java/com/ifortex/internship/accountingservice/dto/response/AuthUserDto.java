package com.ifortex.internship.accountingservice.dto.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class AuthUserDto {
    String accountId;
    String email;
    boolean isTwoFactorEnabled;
    boolean isSoftDeleted;
    boolean isBlocked;
}
