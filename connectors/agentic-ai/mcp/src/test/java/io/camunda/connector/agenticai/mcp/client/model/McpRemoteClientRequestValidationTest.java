/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.mcp.client.model.McpConnectorModeConfiguration.ToolModeConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientRequest.McpRemoteClientRequestData;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientConnection;
import io.camunda.connector.agenticai.mcp.client.model.auth.NoAuthentication;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@Import(ValidationAutoConfiguration.class)
class McpRemoteClientRequestValidationTest {

  @Autowired private Validator validator;

  @Test
  void validationSucceedsForValidRequest() {
    final var request = createValidRequest();

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void validationSucceedsWhenOptionsAreNull() {
    final var transport = createValidTransport();
    final var tools = createValidToolsConfiguration();
    final var connectorMode = createValidConnectorMode(tools);
    final var requestData = new McpRemoteClientRequestData(transport, null, connectorMode);
    final var request = new McpRemoteClientRequest(requestData);

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void validationSucceedsWhenToolsConfigurationIsNull() {
    final var transport = createValidTransport();
    final var options = createOptionsConfiguration(false);
    final var connectorMode =
        new McpConnectorModeConfiguration.ToolModeConfiguration(
            new McpClientOperationConfiguration("tools/list", Map.of()),
            new McpClientToolModeFiltersConfiguration(null));
    final var requestData = new McpRemoteClientRequestData(transport, options, connectorMode);
    final var request = new McpRemoteClientRequest(requestData);

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void validationSucceedsWhenToolsIncludedAndExcludedAreNull() {
    final var transport = createValidTransport();
    final var options = createOptionsConfiguration(false);
    final var tools = new McpClientToolsFilterConfiguration(null, null);
    final var connectorMode = createValidConnectorMode(tools);
    final var requestData = new McpRemoteClientRequestData(transport, options, connectorMode);
    final var request = new McpRemoteClientRequest(requestData);

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void validationSucceedsWhenOperationParamsAreNull() {
    final var transport = createValidTransport();
    final var options = createOptionsConfiguration(false);
    final var tools = createValidToolsConfiguration();
    final var toolOperation = new McpClientOperationConfiguration("tools/list", null);
    final var connectorMode =
        new ToolModeConfiguration(toolOperation, new McpClientToolModeFiltersConfiguration(tools));
    final var requestData = new McpRemoteClientRequestData(transport, options, connectorMode);
    final var request = new McpRemoteClientRequest(requestData);

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void validationFailsWhenRequestDataIsNull() {
    final var request = new McpRemoteClientRequest(null);

    assertThat(validator.validate(request))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be null") || message.contains("null"));
  }

  @Test
  void validationFailsWhenTransportIsNull() {
    final var options = createOptionsConfiguration(false);
    final var connectorMode = createValidConnectorMode();
    final var requestData = new McpRemoteClientRequestData(null, options, connectorMode);
    final var request = new McpRemoteClientRequest(requestData);

    assertThat(validator.validate(request))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be null") || message.contains("null"));
  }

  @Test
  void validationFailsWhenConnectorModeIsNull() {
    final var transport = createValidTransport();
    final var options = createOptionsConfiguration(false);
    final var tools = createValidToolsConfiguration();
    final var requestData = new McpRemoteClientRequestData(transport, options, null);
    final var request = new McpRemoteClientRequest(requestData);

    assertThat(validator.validate(request))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be null") || message.contains("null"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "  "})
  void validationFailsWhenOperationMethodIsBlank(String method) {
    final var transport = createValidTransport();
    final var options = createOptionsConfiguration(false);
    final var tools = createValidToolsConfiguration();
    final var toolOperation = new McpClientOperationConfiguration(method, Map.of());
    final var connectorMode =
        new ToolModeConfiguration(toolOperation, new McpClientToolModeFiltersConfiguration(tools));
    final var requestData = new McpRemoteClientRequestData(transport, options, connectorMode);
    final var request = new McpRemoteClientRequest(requestData);

    assertThat(validator.validate(request))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
  }

  @Test
  void validationFailsWhenOperationMethodIsNull() {
    final var transport = createValidTransport();
    final var options = createOptionsConfiguration(false);
    final var tools = createValidToolsConfiguration();
    final var toolOperation = new McpClientOperationConfiguration(null, Map.of());
    final var connectorMode =
        new ToolModeConfiguration(toolOperation, new McpClientToolModeFiltersConfiguration(tools));
    final var requestData = new McpRemoteClientRequestData(transport, options, connectorMode);
    final var request = new McpRemoteClientRequest(requestData);

    assertThat(validator.validate(request))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
  }

  @Test
  void validationFailsWhenTransportUrlIsBlank() {
    final var transport =
        new StreamableHttpMcpRemoteClientTransportConfiguration(
            new StreamableHttpMcpRemoteClientConnection(
                new NoAuthentication(), "", Collections.emptyMap(), null));
    final var options = createOptionsConfiguration(false);
    final var tools = createValidToolsConfiguration();
    final var connectorMode = createValidConnectorMode();
    final var requestData = new McpRemoteClientRequestData(transport, options, connectorMode);
    final var request = new McpRemoteClientRequest(requestData);

    assertThat(validator.validate(request))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
  }

  @Test
  void validationFailsWhenTransportConnectionIsNull() {
    final var transport = new StreamableHttpMcpRemoteClientTransportConfiguration(null);
    final var options = createOptionsConfiguration(false);
    final var tools = createValidToolsConfiguration();
    final var connectorMode = createValidConnectorMode();
    final var requestData = new McpRemoteClientRequestData(transport, options, connectorMode);
    final var request = new McpRemoteClientRequest(requestData);

    assertThat(validator.validate(request))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be null") || message.contains("null"));
  }

  private McpRemoteClientRequest createValidRequest() {
    final var transport = createValidTransport();
    final var options = createOptionsConfiguration(false);
    final var tools = createValidToolsConfiguration();
    final var connectorMode = createValidConnectorMode();
    final var requestData = new McpRemoteClientRequestData(transport, options, connectorMode);
    return new McpRemoteClientRequest(requestData);
  }

  private StreamableHttpMcpRemoteClientTransportConfiguration createValidTransport() {
    return new StreamableHttpMcpRemoteClientTransportConfiguration(
        new StreamableHttpMcpRemoteClientConnection(
            new NoAuthentication(),
            "http://localhost:8080/mcp",
            Map.of("Authorization", "Bearer token"),
            Duration.ofSeconds(30)));
  }

  private McpRemoteClientOptionsConfiguration createOptionsConfiguration(boolean clientCache) {
    return new McpRemoteClientOptionsConfiguration(clientCache);
  }

  private McpClientToolsFilterConfiguration createValidToolsConfiguration() {
    return new McpClientToolsFilterConfiguration(
        List.of("tool1", "tool2"), List.of("excludedTool"));
  }

  private McpConnectorModeConfiguration createValidConnectorMode(
      McpClientToolsFilterConfiguration toolFilters) {
    final var toolOperation =
        new McpClientOperationConfiguration(
            "tools/call", Map.of("name", "tool1", "arguments", Map.of("param", "value")));
    return new ToolModeConfiguration(
        toolOperation, new McpClientToolModeFiltersConfiguration(toolFilters));
  }

  private McpConnectorModeConfiguration createValidConnectorMode() {
    return createValidConnectorMode(createValidToolsConfiguration());
  }
}
