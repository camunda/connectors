/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * The annotated string is an absolute HTTP or HTTPS URL: the scheme {@code http} or {@code https}
 * in any case, a non-empty authority (host, optionally with user info and port), and no whitespace.
 * {@code null} is valid; combine with {@code @NotBlank} where a value is required.
 */
@Documented
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = {})
@Pattern(
    regexp = "^https?://[^\\s/?#]+([/?#]\\S*)?$",
    flags = Pattern.Flag.CASE_INSENSITIVE,
    message = "Must be an HTTP or HTTPS URL")
public @interface HttpUrl {
  String message() default "Must be an HTTP or HTTPS URL";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
