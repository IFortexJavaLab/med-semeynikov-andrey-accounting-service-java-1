package com.ifortex.internship.authservice.service.impl;

import com.ifortex.internship.authservice.service.RedisService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisServiceImpl implements RedisService {

  private final RedisTemplate<String, Object> redisTemplate;

  public void saveOtp(String key, String otp, long ttlInMinutes) {
    redisTemplate.opsForValue().set(key, otp, ttlInMinutes, TimeUnit.MINUTES);
  }

  public String getOtp(String key) {
    return (String) redisTemplate.opsForValue().get(key);
  }

  public void deleteOtp(String key) {
    redisTemplate.delete(key);
  }
}
