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
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ElementTemplate(
    id = "io.camunda.connectors.agenticai.a2a.client.webhook.v0",
    version = 0,
    name = "A2A Client Webhook Connector (early access)",
    description =
        "Agent-to-Agent (A2A) webhook inbound connector that can be used to receive callbacks from remote A2A servers.",
    documentationRef =
        "https://docs.camunda.io/docs/8.9/components/early-access/alpha/a2a-client/a2a-client-webhook-connector/",
    icon = "a2a-client.svg",
    engineVersion = "^8.9",
    category = @ElementTemplate.Category(id = "aiTools", name = "AI Tools"),
    inputDataClass = A2aWebhookPropertiesWrapper.class,
    defaultResultExpression = "={response: request.body}",
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

  @Nullable private A2aWebhookProperties props;
  @Nullable private WebhookAuthorizationHandler<?> authChecker;
  @Nullable private InboundConnectorContext context;
  @Nullable private HMACVerifier hmacVerifier;

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
    final var activeProps = Objects.requireNonNull(props);
    final var activeAuthChecker = Objects.requireNonNull(authChecker);
    final var activeContext = Objects.requireNonNull(context);
    final var activeHmacVerifier = Objects.requireNonNull(hmacVerifier);

    LOGGER.debug("Triggered A2A webhook with context {}", activeProps.context());

    verifyHmac(payload, activeProps, activeHmacVerifier);

    final var authResult = activeAuthChecker.checkAuthorization(payload);
    if (authResult instanceof Failure failureResult) {
      throw failureResult.toException();
    }

    final var mappedRequest = mapRequest(payload, activeContext);
    return new A2aWebhookResult(mappedRequest);
  }

  private MappedHttpRequest mapRequest(
      WebhookProcessingPayload payload, InboundConnectorContext ctx) {
    try {
      Task task = objectMapper.readValue(payload.rawBody(), Task.TYPE_REFERENCE);
      A2aTask a2aTask = a2aSdkObjectConverter.convert(task);
      return new MappedHttpRequest(a2aTask, payload.headers(), payload.params());
    } catch (IOException e) {
      ctx.log(
          activity ->
              activity
                  .withSeverity(Severity.ERROR)
                  .withTag("a2a-webhook-request")
                  .withMessage("Error deserializing A2A Webhook payload", e));
      throw new RuntimeException(e);
    }
  }

  private void verifyHmac(
      WebhookProcessingPayload payload,
      A2aWebhookProperties activeProps,
      HMACVerifier activeHmacVerifier) {
    if (enabled.equals(activeProps.shouldValidateHmac())) {
      activeHmacVerifier.verifySignature(payload);
    }
  }

  @Override
  public void deactivate() {
    LOGGER.debug("Deactivating A2A Webhook Connector");
    if (context != null) {
      context.reportHealth(Health.down());
    }
  }
}
