package com.ifortex.internship.accountingservice.dto.response;

import com.ifortex.internship.medstarter.security.model.constant.UserRole;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserListViewDto {
    UUID accountId;
    String email;
    String firstName;
    String lastName;
    UserRole role;

}
