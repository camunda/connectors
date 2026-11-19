/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The user prompt delivery {@code reference} needs document references. The violation names the
 * property {@code userPromptDelivery}, so the incident points to the dropdown of the template.
 */
@Documented
@Constraint(validatedBy = UserPromptDeliveryAvailable.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserPromptDeliveryAvailable {

  String message() default
      "The user prompt delivery \"Reference\" requires the option \"Use document references\". Select this option, or select the user prompt delivery \"Content\".";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  class Validator
      implements ConstraintValidator<UserPromptDeliveryAvailable, DocumentsConfiguration> {

    @Override
    public boolean isValid(DocumentsConfiguration documents, ConstraintValidatorContext context) {
      if (documents.references() || documents.userPromptDelivery() != DocumentDelivery.REFERENCE) {
        return true;
      }
      context.disableDefaultConstraintViolation();
      context
          .buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
          .addPropertyNode("userPromptDelivery")
          .addConstraintViolation();
      return false;
    }
  }
}
