package com.ifortex.internship.authserviceapi.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserSearchRequest {
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private List<String> roles;
    private String status;
}