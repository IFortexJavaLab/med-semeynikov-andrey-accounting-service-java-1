package com.ifortex.internship.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

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
