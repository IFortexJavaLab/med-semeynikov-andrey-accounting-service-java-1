package com.ifortex.internship.authservice.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthEntryPointJwt implements AuthenticationEntryPoint {

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    log.debug("Unauthorized error: {}", authException.getMessage());

    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    String jsonResponse =
        String.format(
            "{\"status\": %d, \"message\": \"%s\"}",
            HttpServletResponse.SC_UNAUTHORIZED, "Access is denied, please log in again");

    response.getWriter().write(jsonResponse);
    response.getWriter().flush();
    response.getWriter().close();
  }
}
