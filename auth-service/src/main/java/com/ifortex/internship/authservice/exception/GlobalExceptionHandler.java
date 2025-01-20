package com.ifortex.internship.authservice.exception;

import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(AuthServiceException.class)
  public ResponseEntity<String> handleAuthServiceExceptions(AuthServiceException ex) {

    ResponseStatus statusAnnotation = ex.getClass().getAnnotation(ResponseStatus.class);
    HttpStatus status =
        statusAnnotation != null ? statusAnnotation.value() : HttpStatus.INTERNAL_SERVER_ERROR;

    return new ResponseEntity<>(ex.getMessage(), status);
  }

  // feature handle authentication exceptions instead of UsernameNotFoundException,
  // BadCredentialsException
  @ExceptionHandler(UsernameNotFoundException.class)
  public ResponseEntity<String> handleUsernameNotFoundException(UsernameNotFoundException ex) {
    log.debug("UsernameNotFoundException occurred: {}", ex.getMessage());
    log.info("Login attempt failed: invalid email provided.");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<String> handleBadCredentialsException(BadCredentialsException ex) {
    log.debug("BadCredentialsException occurred: {}", ex.getMessage());
    log.info("Login attempt failed: invalid email or password provided.");
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid email or password");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidationExceptions(
      MethodArgumentNotValidException ex) {

    log.debug(ex.getMessage());

    BindingResult bindingResult = ex.getBindingResult();

    Map<String, String> errors = new HashMap<>();
    bindingResult
        .getFieldErrors()
        .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

    return ResponseEntity.badRequest().body(errors);
  }

  @ExceptionHandler(MissingRequestCookieException.class)
  public ResponseEntity<Object> handleMissingRequestCookieException(
      MissingRequestCookieException ex) {
    String errorMessage = String.format("Required cookie '%s' is missing", ex.getCookieName());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleOtherExceptions(Exception ex) {
    log.error(ex.toString());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body("An unexpected error occurred");
  }
}
