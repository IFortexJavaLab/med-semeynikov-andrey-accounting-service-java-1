package com.ifortex.internship.accountingapi;

import com.ifortex.internship.accountingapi.config.FeignClientConfiguration;
import com.ifortex.internship.accountingapi.dto.response.AccountDto;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
    name = "accounting-service",
    path = "/api/v1",
    configuration = FeignClientConfiguration.class)
public interface AccountingServiceApi {

    /**
     * Retrieves a user profile by their accountId from the accounting-service.
     *
     * <p>This method calls the accounting-service endpoint to fetch a user's data by userId and returns it
     * as a {@link AccountDto}.
     * @param accountId the accountId of the user to retrieve.
     * @return a {@code ResponseEntity} containing the {@code AccountDto} with user details, or a {@code ResponseEntity} with an appropriate HTTP
     * status if the user is not found.
     * @throws FeignException if there is an issue with the communication with the auth-service.
     */
    @GetMapping("/accounting/{accountId}")
    ResponseEntity<?> getUserProfileById(@PathVariable("accountId") UUID accountId);

    /**
     * Retrieves a user profile by their authentication from the accounting-service.
     *
     * <p>This method calls the accounting-service endpoint to fetch a user's data by authentication and returns it
     * as a {@link AccountDto}.
     * @return a {@code ResponseEntity} containing the {@code AccountDto} with user details, or a {@code ResponseEntity} with an appropriate HTTP
     * status if the user is not found.
     * @throws FeignException if there is an issue with the communication with the auth-service.
     */
    @GetMapping("/account")
    ResponseEntity<?> getUserProfileByAuthentication();
}
