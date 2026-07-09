/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.appintegrations.model.CreateChannelRequest;
import io.camunda.connector.appintegrations.model.CreateChannelResult;
import io.camunda.connector.appintegrations.model.LinkedResource;
import io.camunda.connector.appintegrations.model.SendMessageRequest;
import io.camunda.connector.appintegrations.model.SendMessageResult;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.http.client.client.HttpClient;
import io.camunda.connector.http.client.client.apache.CustomApacheHttpClient;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(name = "App Integrations Connector", type = "io.camunda:app-integrations")
@ElementTemplate(
    id = "io.camunda.connectors.AppIntegrations.v1",
    name = "App Integrations Connector",
    version = 1,
    description = "Send notifications and manage channels via Microsoft Teams",
    keywords = {
      "teams",
      "microsoft teams",
      "send message",
      "notification",
      "channel",
      "adaptive card"
    },
    icon = "icon.svg",
    engineVersion = "^8.9",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
      @ElementTemplate.PropertyGroup(
          id = "configuration",
          label = "Configuration",
          openByDefault = false),
      @ElementTemplate.PropertyGroup(
          id = "authentication",
          label = "Authentication",
          openByDefault = false),
      @ElementTemplate.PropertyGroup(id = "message", label = "Message"),
      @ElementTemplate.PropertyGroup(id = "form", label = "Form"),
      @ElementTemplate.PropertyGroup(id = "channel", label = "Channel")
    })
public class AppIntegrationsConnector implements OutboundConnectorProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(AppIntegrationsConnector.class);

  private final ObjectMapper objectMapper;
  private final AppIntegrationsExecutor executor;

  public AppIntegrationsConnector() {
    this(ConnectorsObjectMapperSupplier.getCopy(), new CustomApacheHttpClient(), System::getenv);
  }

  AppIntegrationsConnector(ObjectMapper objectMapper, HttpClient httpClient) {
    this(objectMapper, httpClient, System::getenv);
  }

  AppIntegrationsConnector(
      ObjectMapper objectMapper, HttpClient httpClient, UnaryOperator<String> getenv) {
    this.objectMapper = objectMapper;
    this.executor = new AppIntegrationsExecutor(objectMapper, httpClient, getenv);
  }

  @Operation(id = "sendMessage", name = "Send Message")
  public SendMessageResult sendMessage(
      @Variable SendMessageRequest request, OutboundConnectorContext context) {
    LOGGER.debug("Sending message via App Integrations connector");
    var formResourceKey = formResourceKey(context.getJobContext().getCustomHeaders());

    boolean hasContent =
        (request.message() != null && !request.message().isBlank())
            || (request.adaptiveCardJson() != null && !request.adaptiveCardJson().isBlank())
            || formResourceKey != null;
    if (!hasContent) {
      throw new ConnectorException(
          "VALIDATION_ERROR",
          "One of 'message', 'adaptiveCardJson', or a linked form must be provided");
    }

    return executor.sendMessage(request, formResourceKey);
  }

  @Operation(id = "createChannel", name = "Create Channel")
  public CreateChannelResult createChannel(@Variable CreateChannelRequest request) {
    LOGGER.debug("Creating Teams channel via App Integrations connector");
    return executor.createChannel(request);
  }

  /**
   * Returns the resource key of the linked form (if any) from the {@code linkedResources} custom
   * header (a JSON array) on the activated job, or {@code null} if the header is absent, malformed,
   * or carries no form entry.
   */
  private String formResourceKey(Map<String, String> customHeaders) {
    var raw = customHeaders.get("linkedResources");
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      List<LinkedResource> linkedResources = objectMapper.readValue(raw, new TypeReference<>() {});
      return linkedResources.stream()
          .filter(r -> "form".equalsIgnoreCase(r.resourceType()))
          .map(LinkedResource::resourceKey)
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      LOGGER.warn("Failed to parse linkedResources header, treating as empty: {}", e.getMessage());
      return null;
    }
  }
}
