package com.ifortex.internship.authservice.exception.custom;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidRequestException extends AuthenticationServiceException {
  public InvalidRequestException(String msg) {
    super(msg);
  }
}
