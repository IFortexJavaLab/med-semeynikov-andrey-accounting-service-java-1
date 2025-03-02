package com.ifortex.internship.authserviceapi.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class UserListViewDto {
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private List<String> roles;
}
