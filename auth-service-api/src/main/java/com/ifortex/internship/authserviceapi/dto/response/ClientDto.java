package com.ifortex.internship.authserviceapi.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@Builder
public class ClientDto {
    private String userId;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private boolean isTwoFactorEnabled;
    private List<String> roles;
}
