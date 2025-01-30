package com.ifortex.internship.authservice.filter;

import com.ifortex.internship.authservice.exception.AuthServiceException;
import com.ifortex.internship.authservice.exception.custom.AuthorizationException;
import com.ifortex.internship.authservice.model.UserDetailsImpl;
import com.ifortex.internship.authservice.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {

  private final TokenService tokenService;

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {
    try {

      log.debug("AuthTokenFilter started");

      String jwt = parseJwt(request);

      if (jwt == null) {
        filterChain.doFilter(request, response);
        return;
      }

      if (tokenService.isValid(jwt)) {
        authenticateUser(jwt, request);
        filterChain.doFilter(request, response);
        return;
      }

      throw new AuthorizationException("Invalid JWT token");

    } catch (AuthServiceException e) {
      log.debug("Authentication service exception message: {}", e.getMessage());
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    } catch (Exception e) {
      log.debug("Cannot set user authentication: {}", e.getMessage());
    }
    filterChain.doFilter(request, response);
  }

  private void authenticateUser(String jwt, HttpServletRequest request) {

    log.debug("Authentication user started");

    String username = tokenService.getUsernameFromToken(jwt);
    String userId = tokenService.getUserIdFromToken(jwt);
    Collection<? extends GrantedAuthority> authorities = tokenService.getAuthorityFromToken(jwt);

    UserDetailsImpl userDetails =
        UserDetailsImpl.builder().email(username).userId(userId).authorities(authorities).build();

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(userDetails, jwt, authorities);

    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

    SecurityContextHolder.getContext().setAuthentication(authentication);

    log.debug("Set authentication for user: {}", userDetails.getEmail());
  }

  private String parseJwt(HttpServletRequest request) {
    int BEARER_PREFIX_LENGTH = 7;
    String headerAuth = request.getHeader("Authorization");
    return headerAuth != null && headerAuth.startsWith("Bearer ")
        ? headerAuth.substring(BEARER_PREFIX_LENGTH)
        : null;
  }
}
