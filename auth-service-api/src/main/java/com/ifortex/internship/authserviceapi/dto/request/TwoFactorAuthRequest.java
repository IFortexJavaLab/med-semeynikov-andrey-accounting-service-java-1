package com.ifortex.internship.authserviceapi.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TwoFactorAuthRequest {
    private Boolean enabled;
}
