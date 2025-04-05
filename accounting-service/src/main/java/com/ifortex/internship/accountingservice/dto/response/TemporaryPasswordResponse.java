package com.ifortex.internship.accountingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TemporaryPasswordResponse {
    private String tempPassword;
    private int passwordExpirationH;
}
