package com.ifortex.internship.authservice.service;

/**
 * Service interface for managing Redis operations, such as storing, retrieving, and deleting data.
 */
public interface RedisService {

    /**
     * Saves a value in Redis with a specified key and time-to-live (TTL).
     *
     * @param key          the key under which the value will be stored
     * @param value        the value to be stored
     * @param ttlInMinutes the time-to-live (TTL) for the value, in minutes
     */
    void saveOtp(String key, String value, long ttlInMinutes);

    /**
     * Retrieves a value from Redis by its key.
     *
     * @param key the key associated with the value
     * @return the value associated with the key, or {@code null} if the key does not exist
     */
    String getOtp(String key);

    /**
     * Deletes a value from Redis by its key.
     *
     * @param key the key associated with the value to be deleted
     */
    void deleteOtp(String key);
}