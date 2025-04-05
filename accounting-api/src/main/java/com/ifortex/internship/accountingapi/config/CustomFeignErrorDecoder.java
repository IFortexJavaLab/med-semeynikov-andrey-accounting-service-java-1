package com.ifortex.internship.accountingapi.config;

import com.ifortex.internship.accountingapi.exception.CustomFeignException;
import feign.Response;
import feign.codec.ErrorDecoder;

public class CustomFeignErrorDecoder implements ErrorDecoder {

  @Override
  public Exception decode(String methodKey, Response response) {

    // feature get error message from other service
    int statusCode = response.status();

    String errorMessage = "Unknown error occurred";
    if (response.body() != null) {
      try {
        errorMessage = response.body().toString();
      } catch (Exception e) {
        errorMessage = "Failed to extract error message";
      }
    }

    return new CustomFeignException(statusCode, errorMessage);
  }
}
