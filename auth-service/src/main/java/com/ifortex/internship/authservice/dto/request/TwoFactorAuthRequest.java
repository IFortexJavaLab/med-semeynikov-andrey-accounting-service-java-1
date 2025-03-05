package com.ifortex.internship.authservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class TwoFactorAuthRequest {
    private Boolean isTwoFactorEnabled;
}
