/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(name = "App Integrations", type = "io.camunda:app-integrations:1")
@ElementTemplate(
    id = "io.camunda.connector.AppIntegrations.v1",
    name = "App Integrations Connector",
    version = 1,
    description = "Send notifications and manage channels via Microsoft Teams",
    icon = "icon.svg",
    engineVersion = "^8.8",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
      @ElementTemplate.PropertyGroup(id = "configuration", label = "Configuration"),
      @ElementTemplate.PropertyGroup(id = "message", label = "Message"),
      @ElementTemplate.PropertyGroup(id = "form", label = "Form"),
      @ElementTemplate.PropertyGroup(id = "channel", label = "Channel")
    })
public class AppIntegrationsConnector implements OutboundConnectorProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(AppIntegrationsConnector.class);
  private static final String SEND_MESSAGE_PATH = "/api/connector/message";
  private static final String CREATE_CHANNEL_PATH = "/api/connector/channel";
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private static final String SAAS_ENV_VAR = "CAMUNDA_CONNECTOR_RUNTIME_SAAS";
  private static final String ORG_ID_ENV_VAR = "CAMUNDA_CONNECTOR_CLOUD_ORGANIZATION_ID";
  private static final String CLUSTER_ID_ENV_VAR = "CAMUNDA_CLIENT_CLOUD_CLUSTERID";

  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public AppIntegrationsConnector() {
    this.objectMapper = new ObjectMapper();
    this.httpClient = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build();
  }

  AppIntegrationsConnector(ObjectMapper objectMapper, HttpClient httpClient) {
    this.objectMapper = objectMapper;
    this.httpClient = httpClient;
  }

  @Operation(id = "sendMessage", name = "Send Message")
  public SendMessageResult sendMessage(
      @Variable SendMessageRequest request,
      OutboundConnectorContext context /* reserved for future auth via context.getSecret() */) {
    LOGGER.debug("Sending message via App Integrations connector");

    try {
      var config = request.configuration();

      var linkedResources = parseLinkedResources(context.getJobContext().getCustomHeaders());
      var formResourceKey =
          linkedResources.stream()
              .filter(r -> "form".equalsIgnoreCase(r.resourceType()))
              .map(LinkedResource::resourceKey)
              .findFirst()
              .orElse(null);

      LOGGER.debug(
          "linkedResources: {} form entries, formResourceKey={}",
          linkedResources.size(),
          formResourceKey);

      boolean hasContent =
          (request.message() != null && !request.message().isBlank())
              || (request.adaptiveCardJson() != null && !request.adaptiveCardJson().isBlank())
              || formResourceKey != null;
      if (!hasContent) {
        throw new ConnectorException(
            "VALIDATION_ERROR",
            "One of 'message', 'adaptiveCardJson', or a linked form must be provided");
      }

      boolean saas = System.getenv().containsKey(SAAS_ENV_VAR);
      var payload =
          new MessagePayload(
              request.email(),
              request.channelId(),
              request.message(),
              request.adaptiveCardJson(),
              formResourceKey,
              saas ? System.getenv(ORG_ID_ENV_VAR) : null,
              saas ? System.getenv(CLUSTER_ID_ENV_VAR) : null);
      var body = objectMapper.writeValueAsString(payload);
      var httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(config.baseUrl().replaceAll("/+$", "") + SEND_MESSAGE_PATH))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + config.token())
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .timeout(REQUEST_TIMEOUT)
              .build();

      var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      LOGGER.debug("POST {} → {}", SEND_MESSAGE_PATH, response.statusCode());

      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return objectMapper.readValue(response.body(), SendMessageResult.class);
      }

      throw new ConnectorException(
          "APP_INTEGRATIONS_ERROR",
          "App Integrations responded with status %d: %s"
              .formatted(response.statusCode(), response.body()));

    } catch (IOException e) {
      throw new ConnectorException(
          "IO_ERROR", "Failed to communicate with App Integrations: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConnectorException("INTERRUPTED", "Request was interrupted", e);
    }
  }

  @JsonInclude(Include.NON_NULL)
  private record MessagePayload(
      String email,
      String channelId,
      String message,
      String adaptiveCardJson,
      String formResourceKey,
      String orgId,
      String clusterId) {}

  /**
   * Parses the {@code linkedResources} custom header (JSON array) from the activated job. Returns
   * an empty list if the header is absent or malformed.
   */
  private List<LinkedResource> parseLinkedResources(Map<String, String> headers) {
    var raw = headers.get("linkedResources");
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(raw, new TypeReference<List<LinkedResource>>() {});
    } catch (IOException e) {
      LOGGER.warn("Failed to parse linkedResources header, treating as empty: {}", e.getMessage());
      return List.of();
    }
  }

  @Operation(id = "createChannel", name = "Create Channel")
  public CreateChannelResult createChannel(
      @Variable CreateChannelRequest request, OutboundConnectorContext context) {
    LOGGER.debug("Creating Teams channel via App Integrations connector");

    try {
      var config = request.configuration();
      boolean saas = System.getenv().containsKey(SAAS_ENV_VAR);
      var payload =
          new CreateChannelPayload(
              request.teamId(),
              request.displayName(),
              request.description(),
              request.membershipType() != null ? request.membershipType() : "standard",
              saas ? System.getenv(ORG_ID_ENV_VAR) : null,
              saas ? System.getenv(CLUSTER_ID_ENV_VAR) : null);
      var body = objectMapper.writeValueAsString(payload);
      var httpRequest =
          HttpRequest.newBuilder()
              .uri(URI.create(config.baseUrl().replaceAll("/+$", "") + CREATE_CHANNEL_PATH))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + config.token())
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .timeout(REQUEST_TIMEOUT)
              .build();

      var response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      LOGGER.debug("POST {} → {}", CREATE_CHANNEL_PATH, response.statusCode());

      if (response.statusCode() >= 200 && response.statusCode() < 300) {
        return objectMapper.readValue(response.body(), CreateChannelResult.class);
      }

      throw new ConnectorException(
          "APP_INTEGRATIONS_ERROR",
          "App Integrations responded with status %d: %s"
              .formatted(response.statusCode(), response.body()));

    } catch (IOException e) {
      throw new ConnectorException(
          "IO_ERROR", "Failed to communicate with App Integrations: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConnectorException("INTERRUPTED", "Request was interrupted", e);
    }
  }

  @JsonInclude(Include.NON_NULL)
  private record CreateChannelPayload(
      String teamId,
      String displayName,
      String description,
      String membershipType,
      String orgId,
      String clusterId) {}
}
