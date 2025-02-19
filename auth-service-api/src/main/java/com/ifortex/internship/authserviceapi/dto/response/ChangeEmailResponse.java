package com.ifortex.internship.authserviceapi.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ifortex.internship.authserviceapi.dto.AuthUserDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChangeEmailResponse {
  private AuthUserDto user;
  private String message;
  private String link;

  public ChangeEmailResponse(AuthUserDto authUserDto) {
    this.user = authUserDto;
    this.message = null;
  }

  public ChangeEmailResponse(String message, String link) {
    this.user = null;
    this.message = message;
    this.link = link;
  }
}
