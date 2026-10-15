/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.model;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientTransportConfiguration.SseHttpMcpRemoteClientConnection;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration;
import io.camunda.connector.agenticai.mcp.client.model.McpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientTransportConfiguration.StreamableHttpMcpRemoteClientConnection;
import io.camunda.connector.agenticai.mcp.client.model.auth.NoAuthentication;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Collections;
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
class McpRemoteClientTransportConfigurationValidationTest {

  @Autowired private Validator validator;

  @Test
  void validationSucceedsForValidStreamableHttpConfiguration() {
    final var config =
        new StreamableHttpMcpRemoteClientTransportConfiguration(
            new StreamableHttpMcpRemoteClientConnection(
                new NoAuthentication(),
                "http://localhost:8080/mcp",
                Map.of("Authorization", "Bearer token"),
                Duration.ofSeconds(30)));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void validationSucceedsForValidSseConfiguration() {
    final var config =
        new SseHttpMcpRemoteClientTransportConfiguration(
            new SseHttpMcpRemoteClientConnection(
                new NoAuthentication(),
                "http://localhost:8080/sse",
                Map.of("Authorization", "Bearer token"),
                Duration.ofSeconds(30)));

    assertThat(validator.validate(config)).isEmpty();
  }

  @Test
  void validationSucceedsWhenHeadersAreEmpty() {
    final var streamableHttpConfig =
        new StreamableHttpMcpRemoteClientTransportConfiguration(
            new StreamableHttpMcpRemoteClientConnection(
                new NoAuthentication(),
                "http://localhost:8080/mcp",
                Collections.emptyMap(),
                Duration.ofSeconds(30)));

    final var sseConfig =
        new SseHttpMcpRemoteClientTransportConfiguration(
            new SseHttpMcpRemoteClientConnection(
                new NoAuthentication(),
                "http://localhost:8080/sse",
                Collections.emptyMap(),
                Duration.ofSeconds(30)));

    assertThat(validator.validate(streamableHttpConfig)).isEmpty();
    assertThat(validator.validate(sseConfig)).isEmpty();
  }

  @Test
  void validationSucceedsWhenHeadersAreNull() {
    final var streamableHttpConfig =
        new StreamableHttpMcpRemoteClientTransportConfiguration(
            new StreamableHttpMcpRemoteClientConnection(
                new NoAuthentication(), "http://localhost:8080/mcp", null, Duration.ofSeconds(30)));

    final var sseConfig =
        new SseHttpMcpRemoteClientTransportConfiguration(
            new SseHttpMcpRemoteClientConnection(
                new NoAuthentication(), "http://localhost:8080/sse", null, Duration.ofSeconds(30)));

    assertThat(validator.validate(streamableHttpConfig)).isEmpty();
    assertThat(validator.validate(sseConfig)).isEmpty();
  }

  @Test
  void validationSucceedsWhenTimeoutIsNull() {
    final var streamableHttpConfig =
        new StreamableHttpMcpRemoteClientTransportConfiguration(
            new StreamableHttpMcpRemoteClientConnection(
                new NoAuthentication(),
                "http://localhost:8080/mcp",
                Map.of("Authorization", "Bearer token"),
                null));

    final var sseConfig =
        new SseHttpMcpRemoteClientTransportConfiguration(
            new SseHttpMcpRemoteClientConnection(
                new NoAuthentication(),
                "http://localhost:8080/sse",
                Map.of("Authorization", "Bearer token"),
                null));

    assertThat(validator.validate(streamableHttpConfig)).isEmpty();
    assertThat(validator.validate(sseConfig)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "  "})
  void validationFailsWhenStreamableHttpUrlIsBlank(String url) {
    final var config =
        new StreamableHttpMcpRemoteClientTransportConfiguration(
            new StreamableHttpMcpRemoteClientConnection(
                new NoAuthentication(),
                url,
                Map.of("Authorization", "Bearer token"),
                Duration.ofSeconds(30)));

    assertThat(validator.validate(config))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "  "})
  void validationFailsWhenSseUrlIsBlank(String url) {
    final var config =
        new SseHttpMcpRemoteClientTransportConfiguration(
            new SseHttpMcpRemoteClientConnection(
                new NoAuthentication(),
                url,
                Map.of("Authorization", "Bearer token"),
                Duration.ofSeconds(30)));

    assertThat(validator.validate(config))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
  }

  @Test
  void validationFailsWhenStreamableHttpConnectionIsNull() {
    final var config = new StreamableHttpMcpRemoteClientTransportConfiguration(null);

    assertThat(validator.validate(config))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be null") || message.contains("null"));
  }

  @Test
  void validationFailsWhenSseConnectionIsNull() {
    final var config = new SseHttpMcpRemoteClientTransportConfiguration(null);

    assertThat(validator.validate(config))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be null") || message.contains("null"));
  }

  @Test
  void validationFailsWhenStreamableHttpUrlIsNull() {
    final var config =
        new StreamableHttpMcpRemoteClientTransportConfiguration(
            new StreamableHttpMcpRemoteClientConnection(
                new NoAuthentication(),
                null,
                Map.of("Authorization", "Bearer token"),
                Duration.ofSeconds(30)));

    assertThat(validator.validate(config))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
  }

  @Test
  void validationFailsWhenSseUrlIsNull() {
    final var config =
        new SseHttpMcpRemoteClientTransportConfiguration(
            new SseHttpMcpRemoteClientConnection(
                new NoAuthentication(),
                null,
                Map.of("Authorization", "Bearer token"),
                Duration.ofSeconds(30)));

    assertThat(validator.validate(config))
        .isNotEmpty()
        .extracting(ConstraintViolation::getMessage)
        .anyMatch(message -> message.contains("must not be blank") || message.contains("blank"));
  }
}
