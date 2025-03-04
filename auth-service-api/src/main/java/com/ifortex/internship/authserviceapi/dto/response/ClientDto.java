package com.ifortex.internship.authserviceapi.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class ClientDto {
    private String accountId;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
    private boolean isTwoFactorEnabled;
    //todo add more info like subscription or account Type
}
