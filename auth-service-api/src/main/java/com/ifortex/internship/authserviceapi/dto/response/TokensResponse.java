package com.ifortex.internship.authserviceapi.dto.response;

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
  private Integer accessTokenExpirationMs;
  private Integer refreshTokenExpirationS;
}
