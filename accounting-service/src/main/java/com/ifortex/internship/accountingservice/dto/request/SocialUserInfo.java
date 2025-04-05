package com.ifortex.internship.accountingservice.dto.request;

import com.ifortex.internship.accountingservice.model.constant.Provider;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@Accessors(chain = true)
public class SocialUserInfo {
    String email;
    String firstName;
    String lastName;
    Provider provider;
}
