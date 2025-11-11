/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.StandaloneModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.CallToolOperationConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpStandaloneOperationConfiguration.ListToolsOperationConfiguration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(ValidationAutoConfiguration.class)
class McpConnectorModeConfigurationValidationTest {

  @Autowired private Validator validator;

  @Test
  void validationSucceedsForValidToolModeConfiguration() {
    final var toolOperation = new McpClientOperationConfiguration("tools/call", Map.of());
    final var mode = new ToolModeConfiguration(toolOperation);

    assertThat(validator.validate(mode)).isEmpty();
  }

  @Test
  void validationSucceedsForValidStandaloneModeWithListTools() {
    final var operation = new ListToolsOperationConfiguration();
    final var mode = new StandaloneModeConfiguration(operation);

    assertThat(validator.validate(mode)).isEmpty();
  }

  @Test
  void validationSucceedsForValidStandaloneModeWithCallTool() {
    final var operation = new CallToolOperationConfiguration("myTool", Map.of("arg1", "value1"));
    final var mode = new StandaloneModeConfiguration(operation);

    assertThat(validator.validate(mode)).isEmpty();
  }

  @Test
  void validationSucceedsWhenCallToolArgumentsAreNull() {
    final var operation = new CallToolOperationConfiguration("myTool", null);
    final var mode = new StandaloneModeConfiguration(operation);

    assertThat(validator.validate(mode)).isEmpty();
  }

  @Test
  void validationSucceedsWhenToolOperationParamsAreNull() {
    final var toolOperation = new McpClientOperationConfiguration("tools/list", null);
    final var mode = new ToolModeConfiguration(toolOperation);

    assertThat(validator.validate(mode)).isEmpty();
  }

  @Test
  void validationFailsWhenToolOperationIsNull() {
    final var mode = new ToolModeConfiguration(null);

    assertThat(validator.validate(mode))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be null") || message.contains("null"));
  }

  @Test
  void validationFailsWhenStandaloneOperationIsNull() {
    final var mode = new StandaloneModeConfiguration(null);

    assertThat(validator.validate(mode))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be null") || message.contains("null"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "  "})
  void validationFailsWhenToolOperationMethodIsBlank(String method) {
    final var toolOperation = new McpClientOperationConfiguration(method, Map.of());
    final var mode = new ToolModeConfiguration(toolOperation);

    assertThat(validator.validate(mode))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
  }

  @Test
  void validationFailsWhenToolOperationMethodIsNull() {
    final var toolOperation = new McpClientOperationConfiguration(null, Map.of());
    final var mode = new ToolModeConfiguration(toolOperation);

    assertThat(validator.validate(mode))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "  "})
  void validationFailsWhenCallToolNameIsBlank(String name) {
    final var operation = new CallToolOperationConfiguration(name, Map.of());
    final var mode = new StandaloneModeConfiguration(operation);

    assertThat(validator.validate(mode))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
  }

  @Test
  void validationFailsWhenCallToolNameIsNull() {
    final var operation = new CallToolOperationConfiguration(null, Map.of());
    final var mode = new StandaloneModeConfiguration(operation);

    assertThat(validator.validate(mode))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
  }
}
