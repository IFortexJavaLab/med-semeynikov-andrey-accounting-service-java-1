package com.ifortex.internship.accountingservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TokensResponse {
    private String accessToken;
    private String refreshToken;
    private Long accessTokenExpirationS;
    private Long refreshTokenExpirationS;
}
