package com.ifortex.internship.authservice.exception.custom;

import com.ifortex.internship.authservice.exception.AuthServiceException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class StripeServiceException extends AuthServiceException {
    public StripeServiceException(String message) {
        super(message);
    }
}
