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
import io.camunda.connector.appintegrations.model.AdditionalContent;
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
    version = 2,
    description = "Send notifications and manage channels via Microsoft Teams and Slack",
    keywords = {
      "teams",
      "microsoft teams",
      "slack",
      "send message",
      "notification",
      "channel",
      "adaptive card",
      "block kit",
      "form"
    },
    icon = "icon.svg",
    engineVersion = "^8.10",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
      @ElementTemplate.PropertyGroup(id = "recipient", label = "Recipient"),
      @ElementTemplate.PropertyGroup(id = "message", label = "Message"),
      @ElementTemplate.PropertyGroup(id = "channel", label = "Channel")
    })
public class AppIntegrationsConnector implements OutboundConnectorProvider {

  private static final Logger LOGGER = LoggerFactory.getLogger(AppIntegrationsConnector.class);

  private final ObjectMapper objectMapper;
  private final AppIntegrationsExecutor executor;

  public AppIntegrationsConnector() {
    this(ConnectorsObjectMapperSupplier.getCopy(), new CustomApacheHttpClient(), System::getenv);
  }

  /**
   * All configuration comes from the environment, so tests must inject their own {@code getenv}
   * rather than inheriting the real process environment.
   */
  AppIntegrationsConnector(
      ObjectMapper objectMapper, HttpClient httpClient, UnaryOperator<String> getenv) {
    this.objectMapper = objectMapper;
    this.executor = new AppIntegrationsExecutor(objectMapper, httpClient, getenv);
  }

  @Operation(
      id = "sendMessage",
      name = "Send Message",
      // The description must name the connector: OperationDescriptionConnectorNameRule requires a
      // significant word from the template name ("App Integrations") to appear in every leaf step.
      description =
          "Send an App Integrations message to Microsoft Teams, Slack, or a Camunda recipient —"
              + " plain text plus an optional adaptive card, Block Kit payload, or form",
      keywords = {
        "send message",
        "post message",
        "notify user",
        "send adaptive card",
        "send block kit",
        "send form",
        "teams notification",
        "slack notification"
      })
  public SendMessageResult sendMessage(
      @Variable SendMessageRequest request, OutboundConnectorContext context) {
    LOGGER.debug("Sending message via App Integrations connector");

    // The per-platform sealed hierarchies already guarantee at most one kind of additional content.
    // What they cannot guarantee is that a form actually reached the job: the form is a linked
    // resource, not a variable. A linkedResources header present for any other selection is
    // ignored.
    String formResourceKey = null;
    if (request.recipient().additionalContent() instanceof AdditionalContent.Form) {
      formResourceKey = formResourceKey(context.getJobContext().getCustomHeaders());
      if (formResourceKey == null) {
        throw new ConnectorException(
            "VALIDATION_ERROR",
            "Additional content is 'form' but no linked form was found on the job");
      }
    }

    return executor.sendMessage(request, formResourceKey);
  }

  @Operation(
      id = "createChannel",
      name = "Create Channel",
      description = "Create an App Integrations channel in Microsoft Teams or Slack",
      keywords = {
        "create channel",
        "new channel",
        "add channel",
        "teams channel",
        "slack channel",
        "open channel"
      })
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
