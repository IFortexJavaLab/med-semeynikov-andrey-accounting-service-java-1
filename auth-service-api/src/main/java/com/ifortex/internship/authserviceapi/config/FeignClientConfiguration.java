package com.ifortex.internship.authserviceapi.config;

import feign.codec.ErrorDecoder;
import feign.okhttp.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientConfiguration {

  @Bean
  public ErrorDecoder errorDecoder() {
    return new CustomFeignErrorDecoder();
  }

  @Bean
  public OkHttpClient client() {
    return new OkHttpClient();
  }

  @Bean
  public FeignClientInterceptor interceptor() {
    return new FeignClientInterceptor();
  }
}
