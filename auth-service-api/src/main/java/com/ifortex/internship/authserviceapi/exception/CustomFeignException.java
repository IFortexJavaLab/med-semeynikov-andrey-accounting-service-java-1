package com.ifortex.internship.authserviceapi.exception;

import lombok.Getter;

@Getter
public class CustomFeignException extends RuntimeException {
  private final int statusCode;

  public CustomFeignException(int statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }
}
