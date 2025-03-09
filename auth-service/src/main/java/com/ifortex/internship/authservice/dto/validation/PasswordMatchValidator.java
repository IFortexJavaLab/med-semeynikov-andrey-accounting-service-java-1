package com.ifortex.internship.authservice.dto.validation;

import com.ifortex.internship.medstarter.exception.custom.InternalServiceException;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.reflect.Field;

public class PasswordMatchValidator implements ConstraintValidator<PasswordMatch, Object> {

    private String passwordField;
    private String confirmPasswordField;
    private String message;

    @Override
    public void initialize(PasswordMatch constraintAnnotation) {
        this.passwordField = constraintAnnotation.passwordField();
        this.confirmPasswordField = constraintAnnotation.confirmPasswordField();
        this.message = constraintAnnotation.message();
    }

    @Override
    public boolean isValid(Object object, ConstraintValidatorContext context) {
        try {
            Field password = object.getClass().getDeclaredField(this.passwordField);
            Field confirmPassword = object.getClass().getDeclaredField(this.confirmPasswordField);

            password.setAccessible(true);
            confirmPassword.setAccessible(true);

            Object passwordValue = password.get(object);
            Object confirmPasswordValue = confirmPassword.get(object);

            if (passwordValue == null || confirmPasswordValue == null) {
                return false;
            }

            boolean isValid = passwordValue.equals(confirmPasswordValue);

            if (!isValid) {
                context.disableDefaultConstraintViolation();
                context
                    .buildConstraintViolationWithTemplate(message)
                    .addPropertyNode(
                        this.confirmPasswordField)
                    .addConstraintViolation();
            }

            return isValid;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new InternalServiceException(String.format("Error accessing password fields. Details: %s", e));
        }
    }
}
