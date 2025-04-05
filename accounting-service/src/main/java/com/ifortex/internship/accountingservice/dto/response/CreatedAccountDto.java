package com.ifortex.internship.accountingservice.dto.response;

import com.ifortex.internship.accountingservice.model.Account;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreatedAccountDto {
    Account account;
    String password;
    int tempPasswordExpirationHours;
}
