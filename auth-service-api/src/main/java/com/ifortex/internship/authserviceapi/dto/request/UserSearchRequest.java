package com.ifortex.internship.authserviceapi.dto.request;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
public class UserSearchRequest {
    private String searchText;
    boolean admins;
    boolean clients;
    boolean medics;
    boolean blocked;
    boolean deleted;
}