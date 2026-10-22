/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.common.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.inbound.InboundConnectorDefinition;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqInboundProperties;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqRequest;
import io.camunda.connector.runtime.test.inbound.InboundConnectorContextBuilder;
import io.camunda.connector.runtime.test.outbound.OutboundConnectorContextBuilder;
import io.camunda.connector.validation.impl.DefaultValidationProvider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Binds inbound properties and outbound variables through the full JSON path, as the runtime does
 * for a Modeler-generated diagram, and checks which source wins.
 */
class RabbitMqCredentialBindingTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final Map<String, Object> CREDENTIALS_CREDENTIAL =
      Map.of(
          "authentication",
          Map.of(
              "authType", "credentials",
              "userName", "credential-user",
              "password", "credential-password"),
          "routing",
          Map.of(
              "virtualHost", "credential-vhost",
              "hostName", "credential-host",
              "port", "5671"));

  private static final Map<String, Object> URI_CREDENTIAL =
      Map.of(
          "authentication",
          Map.of("authType", "uri", "uri", "amqps://user:pass@credential-host:5671/vhost"));

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void usesCredentialWhenNoInlineConnection(boolean inbound) throws Exception {
    var input = input(inbound);
    input.put(RabbitMqConnectionConfiguration.CHOOSER_ID, CREDENTIALS_CREDENTIAL);

    var connection = bind(inbound, input);

    assertThat(connection.authentication())
        .isEqualTo(new CredentialsAuthentication("credential-user", "credential-password"));
    assertThat(connection.routing())
        .isEqualTo(new FactoryRoutingData("credential-vhost", "credential-host", "5671"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void usesUriCredentialAndIgnoresLeftoverInlineDiscriminator(boolean inbound) throws Exception {
    var input = input(inbound);
    input.put(RabbitMqConnectionConfiguration.CHOOSER_ID, URI_CREDENTIAL);
    // Modeler keeps the hidden inline discriminator's default value
    input.put("authentication", Map.of("authType", "uri"));

    var connection = bind(inbound, input);

    assertThat(connection.authentication())
        .isEqualTo(new UriAuthentication("amqps://user:pass@credential-host:5671/vhost"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void credentialWinsOverInlineConnection(boolean inbound) throws Exception {
    var input = input(inbound);
    input.put(RabbitMqConnectionConfiguration.CHOOSER_ID, CREDENTIALS_CREDENTIAL);
    input.put(
        "authentication",
        Map.of("authType", "credentials", "userName", "inline-user", "password", "inline-pass"));
    putInlineRouting(inbound, input, "inline-host");

    var connection = bind(inbound, input);

    assertThat(connection.authentication())
        .isEqualTo(new CredentialsAuthentication("credential-user", "credential-password"));
    assertThat(connection.routing().hostName()).isEqualTo("credential-host");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void usesInlineConnectionWithoutCredential(boolean inbound) throws Exception {
    var input = input(inbound);
    input.put(
        "authentication",
        Map.of("authType", "credentials", "userName", "inline-user", "password", "inline-pass"));
    putInlineRouting(inbound, input, "inline-host");

    var connection = bind(inbound, input);

    assertThat(connection.authentication())
        .isEqualTo(new CredentialsAuthentication("inline-user", "inline-pass"));
    assertThat(connection.routing())
        .isEqualTo(new FactoryRoutingData("inline-vhost", "inline-host", "5672"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsMissingAuthenticationNamingBothSources(boolean inbound) {
    var input = input(inbound);

    assertThatThrownBy(() -> bind(inbound, input))
        .hasMessageContaining(
            "No authentication provided by the reusable credential or the element template");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void stillValidatesInlineAuthenticationWithoutCredential(boolean inbound) {
    var input = input(inbound);
    input.put("authentication", Map.of("authType", "uri", "uri", ""));

    assertThatThrownBy(() -> bind(inbound, input)).hasMessageContaining("uri");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void rejectsUsernamePasswordCredentialWithoutRouting(boolean inbound) {
    var input = input(inbound);
    input.put(
        RabbitMqConnectionConfiguration.CHOOSER_ID,
        Map.of("authentication", CREDENTIALS_CREDENTIAL.get("authentication")));

    assertThatThrownBy(() -> bind(inbound, input))
        .hasMessageContaining(
            "Host name, port and virtual host are required for username/password connections");
  }

  private static Map<String, Object> input(boolean inbound) {
    var input = new HashMap<String, Object>();
    if (inbound) {
      input.put("queueName", "task-queue");
    } else {
      input.put(
          "routing", new HashMap<>(Map.of("exchange", "task-exchange", "routingKey", "task-key")));
      input.put("message", Map.of("body", "hello"));
    }
    return input;
  }

  @SuppressWarnings("unchecked")
  private static void putInlineRouting(
      boolean inbound, Map<String, Object> input, String hostName) {
    var routing = Map.of("virtualHost", "inline-vhost", "hostName", hostName, "port", "5672");
    if (inbound) {
      input.put("routing", routing);
    } else {
      ((Map<String, Object>) input.get("routing")).putAll(routing);
    }
  }

  private static EffectiveConnection bind(boolean inbound, Map<String, Object> input)
      throws Exception {
    if (inbound) {
      var properties =
          InboundConnectorContextBuilder.create()
              .definition(new InboundConnectorDefinition(null, null, null, List.of(), null))
              .properties(input)
              .validation(new DefaultValidationProvider())
              .build()
              .bindProperties(RabbitMqInboundProperties.class);
      return new EffectiveConnection(properties.getAuthentication(), properties.getRouting());
    }
    var request =
        OutboundConnectorContextBuilder.create()
            .variables(MAPPER.writeValueAsString(input))
            .validation(new DefaultValidationProvider())
            .build()
            .bindVariables(RabbitMqRequest.class);
    assertThat(request.routing().exchange()).isEqualTo("task-exchange");
    assertThat(request.routing().routingKey()).isEqualTo("task-key");
    return new EffectiveConnection(request.authentication(), request.routing().routingData());
  }

  private record EffectiveConnection(
      RabbitMqAuthentication authentication, FactoryRoutingData routing) {}
}
