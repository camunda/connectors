/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.a2a.client.inbound.webhook;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.Task;
import io.camunda.connector.agenticai.a2a.client.common.convert.A2aSdkObjectConverter;
import io.camunda.connector.agenticai.a2a.client.common.model.result.A2aTask;
import io.camunda.connector.agenticai.a2a.client.inbound.webhook.model.A2aWebhookProperties;
import io.camunda.connector.agenticai.a2a.client.inbound.webhook.model.A2aWebhookProperties.A2aWebhookPropertiesWrapper;
import io.camunda.connector.agenticai.a2a.client.inbound.webhook.model.A2aWebhookResult;
import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookConnectorExecutable;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookResult;
import io.camunda.connector.generator.java.annotation.BpmnType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.ElementTemplate.ConnectorElementType;
import io.camunda.connector.generator.java.annotation.ElementTemplate.PropertyGroup;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure;
import io.camunda.connector.inbound.authorization.WebhookAuthorizationHandler;
import io.camunda.connector.inbound.signature.HMACVerifier;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: add documentation link when available
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.a2a.client.webhook.v0",
    version = 0,
    name = "A2A Client Webhook Connector (early access)",
    description =
        "Agent-to-Agent (A2A) webhook inbound connector that can be used to receive callbacks from remote A2A servers.",
    icon = "a2a-client.svg",
    engineVersion = "^8.9",
    inputDataClass = A2aWebhookPropertiesWrapper.class,
    propertyGroups = {
      @PropertyGroup(id = "endpoint", label = "Webhook configuration"),
      @PropertyGroup(id = "clientResponse", label = "Client Response"),
      @PropertyGroup(id = "authentication", label = "Authentication"),
      @PropertyGroup(id = "authorization", label = "Authorization")
    },
    elementTypes = {
      @ConnectorElementType(
          appliesTo = {BpmnType.INTERMEDIATE_THROW_EVENT, BpmnType.INTERMEDIATE_CATCH_EVENT},
          elementType = BpmnType.INTERMEDIATE_CATCH_EVENT,
          templateIdOverride = "io.camunda.connectors.agenticai.a2a.client.webhook.intermediate.v0",
          templateNameOverride =
              "A2A Client Webhook Intermediate Catch Event Connector (early access)"),
      @ConnectorElementType(
          appliesTo = BpmnType.RECEIVE_TASK,
          elementType = BpmnType.RECEIVE_TASK,
          templateIdOverride = "io.camunda.connectors.agenticai.a2a.client.webhook.receive.v0",
          templateNameOverride = "A2A Client Webhook Receive Task Connector (early access)"),
    })
@InboundConnector(name = "A2A Webhook Connector", type = "io.camunda.agenticai:a2aclient:webhook:0")
public class A2aClientWebhookExecutable implements WebhookConnectorExecutable {

  private static final Logger LOGGER = LoggerFactory.getLogger(A2aClientWebhookExecutable.class);

  private final A2aSdkObjectConverter a2aSdkObjectConverter;
  private final ObjectMapper objectMapper;

  private A2aWebhookProperties props;
  private WebhookAuthorizationHandler<?> authChecker;
  private InboundConnectorContext context;
  private HMACVerifier hmacVerifier;

  public A2aClientWebhookExecutable(
      A2aSdkObjectConverter a2aSdkObjectConverter, ObjectMapper objectMapper) {
    this.a2aSdkObjectConverter = a2aSdkObjectConverter;
    this.objectMapper = objectMapper;
  }

  @Override
  public void activate(InboundConnectorContext context) {
    this.context = context;
    var wrappedProps = context.bindProperties(A2aWebhookPropertiesWrapper.class);
    props = new A2aWebhookProperties(wrappedProps);
    authChecker = WebhookAuthorizationHandler.getHandlerForAuth(props.auth());
    hmacVerifier =
        new HMACVerifier(
            props.hmacScopes(), props.hmacHeader(), props.hmacSecret(), props.hmacAlgorithm());
    context.reportHealth(Health.up());
  }

  @Override
  public WebhookResult triggerWebhook(WebhookProcessingPayload payload) {
    LOGGER.debug("Triggered A2A webhook with context {}", props.context());

    verifyHmac(payload);

    final var authResult = authChecker.checkAuthorization(payload);
    if (authResult instanceof Failure failureResult) {
      throw failureResult.toException();
    }

    final var mappedRequest = mapRequest(payload);
    return new A2aWebhookResult(mappedRequest);
  }

  private MappedHttpRequest mapRequest(WebhookProcessingPayload payload) {
    try {
      Task task = objectMapper.readValue(payload.rawBody(), Task.TYPE_REFERENCE);
      A2aTask a2aTask = a2aSdkObjectConverter.convert(task);
      return new MappedHttpRequest(a2aTask, payload.headers(), payload.params());
    } catch (IOException e) {
      this.context.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("a2a-webhook-request")
                  .withMessage("Error deserializing A2A Webhook payload: " + e.getMessage()));
      throw new RuntimeException(e);
    }
  }

  private void verifyHmac(WebhookProcessingPayload payload) {
    if (enabled.equals(props.shouldValidateHmac())) {
      hmacVerifier.verifySignature(payload);
    }
  }

  @Override
  public void deactivate() {
    LOGGER.debug("Deactivating A2A Webhook Connector");
    context.reportHealth(Health.down());
  }
}
