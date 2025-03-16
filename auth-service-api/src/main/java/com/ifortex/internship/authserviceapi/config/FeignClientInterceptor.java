package com.ifortex.internship.authserviceapi.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class FeignClientInterceptor implements RequestInterceptor {
  @Override
  public void apply(RequestTemplate template) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getCredentials() instanceof String token) {
      template.header("Authorization", "Bearer " + token);
    }
  }
}
