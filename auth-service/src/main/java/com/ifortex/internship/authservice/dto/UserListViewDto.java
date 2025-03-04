package com.ifortex.internship.authservice.dto;

import com.ifortex.internship.authservice.model.constant.RoleType;
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
    RoleType role;

}
