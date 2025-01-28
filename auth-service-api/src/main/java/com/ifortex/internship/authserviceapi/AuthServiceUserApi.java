package com.ifortex.internship.authserviceapi;

import com.ifortex.internship.authserviceapi.config.FeignClientConfiguration;
import com.ifortex.internship.authserviceapi.dto.UserDto;
import com.ifortex.internship.authserviceapi.dto.request.ChangePasswordRequest;
import com.ifortex.internship.authserviceapi.dto.response.SuccessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "auth-service",
    path = "/api/v1/users",
    configuration = FeignClientConfiguration.class)
public interface AuthServiceUserApi {

  /**
   * Changes the password for a user.
   *
   * @param request the change password request containing current and new passwords
   * @return SuccessResponse indicating the password change result
   */
  @PatchMapping("/change-password")
  ResponseEntity<SuccessResponse> changePassword(@RequestBody ChangePasswordRequest request);

  @GetMapping("/{email}")
  ResponseEntity<UserDto> getUserByEmail(@PathVariable("email") String email);
}
