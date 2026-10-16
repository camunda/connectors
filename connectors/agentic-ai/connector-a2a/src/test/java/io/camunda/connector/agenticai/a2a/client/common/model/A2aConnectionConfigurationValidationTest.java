/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.TestConstructor.AutowireMode;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(ValidationAutoConfiguration.class)
@TestConstructor(autowireMode = AutowireMode.ALL)
class A2aConnectionConfigurationValidationTest {

  private final Validator validator;

  A2aConnectionConfigurationValidationTest(Validator validator) {
    this.validator = validator;
  }

  @ParameterizedTest
  @ValueSource(strings = {"http://localhost:8080", "https://agent.example.com/a2a"})
  void validationSucceedsForHttpUrl(String url) {
    assertThat(validator.validate(new A2aConnectionConfiguration(url, null))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"localhost:8080", "ftp://localhost/a2a", "file:///tmp/agent-card.json"})
  void validationFailsWhenUrlIsNotAnHttpUrl(String url) {
    assertThat(validator.validate(new A2aConnectionConfiguration(url, null)))
        .extracting(ConstraintViolation::getMessage)
        .containsExactly("Must be an HTTP or HTTPS URL");
  }
}
