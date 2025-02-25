package com.ifortex.internship.authserviceapi.validation;

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
      Field passwordField = object.getClass().getDeclaredField(this.passwordField);
      Field confirmPasswordField = object.getClass().getDeclaredField(this.confirmPasswordField);

      passwordField.setAccessible(true);
      confirmPasswordField.setAccessible(true);

      Object passwordValue = passwordField.get(object);
      Object confirmPasswordValue = confirmPasswordField.get(object);

      if (passwordValue == null || confirmPasswordValue == null) {
        return false;
      }

      boolean isValid = passwordValue.equals(confirmPasswordValue);

      if (!isValid) {
        context.disableDefaultConstraintViolation();
        context
            .buildConstraintViolationWithTemplate(message)
            .addPropertyNode(
                this.confirmPasswordField) // Ошибка будет привязана к полю подтверждения
            .addConstraintViolation();
      }

      return isValid;
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Error accessing password fields", e);
    }
  }
}
