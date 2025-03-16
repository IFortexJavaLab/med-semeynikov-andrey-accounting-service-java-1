package com.ifortex.internship.authserviceapi;

import com.ifortex.internship.authserviceapi.config.FeignClientConfiguration;
import com.ifortex.internship.authserviceapi.dto.response.AccountDto;
import feign.FeignException;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
    name = "auth-service",
    path = "/api/v1/accounting",
    configuration = FeignClientConfiguration.class)
public interface AccountingServiceApi {

    /**
     * Retrieves a user profile by their accountId from the auth-service.
     *
     * <p>This method calls the auth-service endpoint to fetch a user's data by userId and returns it
     * as a {@link AccountDto}.
     * @param accountId the accountId of the user to retrieve.
     * @return a {@code ResponseEntity} containing the {@code AccountDto} with user details, or a {@code ResponseEntity} with an appropriate HTTP
     * status if the user is not found.
     * @throws FeignException if there is an issue with the communication with the auth-service.
     */
    @GetMapping("/{accountId}")
    ResponseEntity<?> getUserProfileById(@PathVariable("accountId") UUID accountId);
}
