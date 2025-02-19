package com.ifortex.internship.authservice.stripe.exception;

import com.ifortex.internship.authservice.exception.AuthServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ActiveSubscriptionNotFoundException extends AuthServiceException {
  public ActiveSubscriptionNotFoundException(String message) {
    super(message);
  }
}
