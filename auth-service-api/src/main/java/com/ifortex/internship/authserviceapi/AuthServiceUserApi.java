package com.ifortex.internship.authserviceapi;

import com.ifortex.internship.authserviceapi.config.FeignClientConfiguration;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "AUTH-SERVICE",
    path = "/api/v1/users",
    url = "localhost:8081",
    configuration = FeignClientConfiguration.class)
public interface AuthServiceUserApi {

  /**
   * Changes the password for a user. Requires a valid refresh token.
   *
   * @param request the change password request containing current and new passwords
   * @return SuccessResponse indicating the password change result
   */
  @PatchMapping("/change-password")
  ResponseEntity<SuccessResponse> changePassword(@RequestBody ChangePasswordRequest request);
}
