package com.ifortex.internship.authservice.exception.custom;

import com.ifortex.internship.authservice.exception.AuthServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class EmailAlreadyRegistered extends AuthServiceException {
  public EmailAlreadyRegistered(String message) {
    super(message);
  }
}
