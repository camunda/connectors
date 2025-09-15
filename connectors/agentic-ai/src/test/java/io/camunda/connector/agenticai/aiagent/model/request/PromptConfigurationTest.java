/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(ValidationAutoConfiguration.class)
class PromptConfigurationTest {

  @Autowired private Validator validator;

  @Nested
  class SystemPromptConfigurationValidationTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void supportsEmptySystemPrompt(String prompt) {
      final var systemPrompt = new SystemPromptConfiguration(prompt);

      assertThat(validator.validate(systemPrompt)).isEmpty();
      assertThat(systemPrompt.prompt()).isEqualTo(prompt);
    }
  }

  @Nested
  class UserPromptConfigurationValidationTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void doesNotSupportEmptyUserPrompt(String prompt) {
      final var userPrompt = new UserPromptConfiguration(prompt, List.of());

      assertThat(validator.validate(userPrompt))
          .hasSize(1)
          .extracting(c -> c.getPropertyPath().toString(), ConstraintViolation::getMessage)
          .contains(tuple("prompt", "must not be blank"));
    }
  }
}
