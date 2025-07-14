/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.AgentRequest.AgentRequestData.UserPromptConfiguration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(ValidationAutoConfiguration.class)
class AgentRequestTest {

  @Autowired private Validator validator;

  @Nested
  class SystemPromptConfigurationValidationTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void supportsEmptySystemPrompt(String prompt) {
      final var systemPrompt = new SystemPromptConfiguration(prompt, Map.of());

      assertThat(validator.validate(systemPrompt)).isEmpty();
      assertThat(systemPrompt.prompt()).isEqualTo(prompt);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void supportsEmptySystemPromptParameters(Map<String, Object> parameters) {
      final var systemPrompt =
          new SystemPromptConfiguration("You are a helpful assistant named {{name}}", parameters);

      assertThat(validator.validate(systemPrompt)).isEmpty();
      assertThat(systemPrompt.parameters()).isEqualTo(parameters);
    }

    @Test
    void supportsValidSystemPromptParameters() {
      final Map<String, Object> parameters =
          Map.of(
              "name",
              "Johnny",
              "age",
              30,
              "dummy",
              new DummyClass("hello"),
              "a_cOMplicated__Key123",
              "some-value");

      final var systemPrompt =
          new SystemPromptConfiguration("You are a helpful assistant named {{name}}", parameters);

      assertThat(validator.validate(systemPrompt)).isEmpty();
      assertThat(systemPrompt.parameters()).isEqualTo(parameters);
    }

    @Test
    void throwsValidationErrorOnNullSystemPromptParameterKeys() {
      final var systemPrompt =
          new SystemPromptConfiguration(
              "You are a helpful assistant named {{name}}", nullKeyPromptParameters());

      assertThat(validator.validate(systemPrompt))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactlyInAnyOrder("System prompt parameter key must not be blank");
    }

    @Test
    void throwsValidationErrorOnEmptySystemPromptParameterKeys() {
      final var systemPrompt =
          new SystemPromptConfiguration(
              "You are a helpful assistant named {{name}}", Map.of(" ", "value"));

      assertThat(validator.validate(systemPrompt))
          .hasSize(2)
          .extracting(ConstraintViolation::getMessage)
          .containsExactlyInAnyOrder(
              "System prompt parameter key must not be blank",
              "System prompt parameter key can only contain letters, digits, or underscores");
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.AgentRequestTest#invalidPromptParameters")
    void throwsValidationErrorOnInvalidSystemPromptParameterKeys(Map<String, Object> parameters) {
      final var systemPrompt =
          new SystemPromptConfiguration("You are a helpful assistant named {{name}}", parameters);

      assertThat(validator.validate(systemPrompt))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactly(
              "System prompt parameter key can only contain letters, digits, or underscores");
    }
  }

  @Nested
  class UserPromptConfigurationValidationTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void supportsEmptyUserPrompt(String prompt) {
      final var userPrompt = new UserPromptConfiguration(prompt, Map.of(), List.of());

      assertThat(validator.validate(userPrompt)).isEmpty();
      assertThat(userPrompt.prompt()).isEqualTo(prompt);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void supportsEmptyUserPromptParameters(Map<String, Object> parameters) {
      final var userPrompt =
          new UserPromptConfiguration("Tell me a story about {{name}}", parameters, List.of());

      assertThat(validator.validate(userPrompt)).isEmpty();
      assertThat(userPrompt.parameters()).isEqualTo(parameters);
    }

    @Test
    void supportsValidUserPromptParameters() {
      final Map<String, Object> parameters =
          Map.of(
              "name",
              "Johnny",
              "age",
              30,
              "dummy",
              new DummyClass("hello"),
              "a_cOMplicated__Key123",
              "some-value");

      final var userPrompt =
          new UserPromptConfiguration("Tell me a story about {{name}}", parameters, List.of());

      assertThat(validator.validate(userPrompt)).isEmpty();
      assertThat(userPrompt.parameters()).isEqualTo(parameters);
    }

    @Test
    void throwsValidationErrorOnNullUserPromptParameterKeys() {
      final var userPrompt =
          new UserPromptConfiguration(
              "Tell me a story about {{name}}", nullKeyPromptParameters(), List.of());

      assertThat(validator.validate(userPrompt))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactlyInAnyOrder("User prompt parameter key must not be blank");
    }

    @Test
    void throwsValidationErrorOnEmptyUserPromptParameterKeys() {
      final var userPrompt =
          new UserPromptConfiguration(
              "Tell me a story about {{name}}", Map.of(" ", "value"), List.of());

      assertThat(validator.validate(userPrompt))
          .hasSize(2)
          .extracting(ConstraintViolation::getMessage)
          .containsExactlyInAnyOrder(
              "User prompt parameter key must not be blank",
              "User prompt parameter key can only contain letters, digits, or underscores");
    }

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.agenticai.aiagent.model.request.AgentRequestTest#invalidPromptParameters")
    void throwsValidationErrorOnInvalidUserPromptParameterKeys(Map<String, Object> parameters) {
      final var userPrompt =
          new UserPromptConfiguration("Tell me a story about {{name}}", parameters, List.of());

      assertThat(validator.validate(userPrompt))
          .hasSize(1)
          .extracting(ConstraintViolation::getMessage)
          .containsExactly(
              "User prompt parameter key can only contain letters, digits, or underscores");
    }
  }

  static Map<String, Object> nullKeyPromptParameters() {
    Map<String, Object> nullKeyMap = new LinkedHashMap<>();
    nullKeyMap.put(null, "value");
    return nullKeyMap;
  }

  static List<Map<String, Object>> invalidPromptParameters() {
    final var invalidParams = new ArrayList<Map<String, Object>>();
    invalidParams.add(Map.of("a key with spaces", "value"));
    invalidParams.add(Map.of("a-key-with-dashes", "value"));
    invalidParams.add(Map.of("@_key_with_symbols!", "value"));
    return invalidParams;
  }

  private record DummyClass(String name) {}
}
