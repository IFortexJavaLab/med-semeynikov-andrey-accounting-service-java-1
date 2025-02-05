package com.ifortex.internship.authservice.exception.custom;

import com.ifortex.internship.authservice.exception.AuthServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AuthorizationException extends AuthServiceException {
  public AuthorizationException(String message) {
    super(message);
  }
}
