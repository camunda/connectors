/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.core.inbound.correlation;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientStatusException;
import io.camunda.client.api.response.CorrelateMessageResponse;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.response.ProcessInstanceResult;
import io.camunda.client.api.response.PublishMessageResponse;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.ActivationCheckResult;
import io.camunda.connector.api.inbound.CorrelationRequest;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.ActivationConditionNotMet;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.Other;
import io.camunda.connector.api.inbound.CorrelationResult.Success.MessageAlreadyCorrelated;
import io.camunda.connector.api.inbound.ProcessElement;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.ConnectorResultHandler;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.grpc.Status;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Component responsible for calling Zeebe to report an inbound event */
public class InboundCorrelationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(InboundCorrelationHandler.class);

  private final CamundaClient camundaClient;
  private final FeelEngineWrapper feelEngine;
  private final ActivationConditionEvaluator activationConditionEvaluator;

  private final Duration defaultMessageTtl;

  private final ConnectorResultHandler connectorResultHandler;

  public InboundCorrelationHandler(
      CamundaClient camundaClient,
      FeelEngineWrapper feelEngine,
      ObjectMapper objectMapper,
      Duration defaultMessageTtl) {
    this.camundaClient = camundaClient;
    this.feelEngine = feelEngine;
    this.activationConditionEvaluator = new ActivationConditionEvaluator(feelEngine);
    this.defaultMessageTtl = defaultMessageTtl;
    this.connectorResultHandler = new ConnectorResultHandler(objectMapper);
  }

  public CorrelationResult correlate(List<InboundConnectorElement> elements, Object variables) {
    return correlate(elements, CorrelationRequest.builder().variables(variables).build());
  }

  public CorrelationResult correlate(
      List<InboundConnectorElement> elements, CorrelationRequest correlationRequest) {

    final ActivationCheckResult activationCheckResult;
    try {
      activationCheckResult = canActivate(elements, correlationRequest.getVariables());
    } catch (ConnectorInputException e) {
      LOG.info("Failed to evaluate activation condition", e);
      return new CorrelationResult.Failure.InvalidInput(
          "Failed to evaluate activation condition against the provided input", e);
    }

    return switch (activationCheckResult) {
      case ActivationCheckResult.Failure.NoMatchingElement noMatchingElement ->
          new ActivationConditionNotMet(noMatchingElement.discardUnmatchedEvents());
      case ActivationCheckResult.Failure.TooManyMatchingElements tooMany ->
          new Failure.InvalidInput(
              "Multiple connectors are activated for the same input: " + tooMany.reason(), null);
      case ActivationCheckResult.Success.CanActivate canActivate ->
          correlateInternal(
              findMatchingElement(elements, canActivate.activatedElement()),
              correlationRequest.getVariables(),
              correlationRequest.getMessageId());
    };
  }

  protected CorrelationResult correlateInternal(
      InboundConnectorElement activatedElement, Object variables, String messageId) {
    var correlationPoint = activatedElement.correlationPoint();

    return switch (correlationPoint) {
      case StartEventCorrelationPoint corPoint ->
          triggerStartEvent(activatedElement, corPoint, variables);
      case MessageCorrelationPoint corPoint ->
          triggerMessage(
              activatedElement,
              corPoint,
              variables,
              resolveMessageId(corPoint.messageIdExpression(), messageId, variables));
      case MessageStartEventCorrelationPoint corPoint ->
          triggerMessageStartEvent(
              activatedElement,
              corPoint,
              variables,
              resolveMessageId(corPoint.messageIdExpression(), messageId, variables));
    };
  }

  protected CorrelationResult triggerStartEvent(
      InboundConnectorElement activatedElement,
      StartEventCorrelationPoint correlationPoint,
      Object variables) {
    Object extractedVariables = extractVariables(variables, activatedElement);
    if (activatedElement.synchronousResponse()) {
      return triggerStartEventWithResult(activatedElement, correlationPoint, extractedVariables);
    } else {
      return triggerStartEventWithoutResult(activatedElement, correlationPoint, extractedVariables);
    }
  }

  private CorrelationResult triggerStartEventWithoutResult(
      InboundConnectorElement activatedElement,
      StartEventCorrelationPoint correlationPoint,
      Object extractedVariables) {
    try {
      ProcessInstanceEvent result =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(correlationPoint.bpmnProcessId())
              .version(correlationPoint.version())
              .tenantId(activatedElement.tenantId())
              .variables(extractedVariables)
              .send()
              .join();

      LOG.info("Created a process instance with key {}", result.getProcessInstanceKey());
      return new CorrelationResult.Success.ProcessInstanceCreated(
          activatedElement.element(), result.getProcessInstanceKey(), result.getTenantId());

    } catch (ClientStatusException e1) {
      LOG.info("Failed to create process instance: ", e1);
      return new CorrelationResult.Failure.ZeebeClientStatus(
          e1.getStatus().getCode().name(), e1.getMessage());
    } catch (Throwable e2) {
      return new Other(e2);
    }
  }

  private CorrelationResult triggerStartEventWithResult(
      InboundConnectorElement activatedElement,
      StartEventCorrelationPoint correlationPoint,
      Object extractedVariables) {
    try {
      ProcessInstanceResult result =
          camundaClient
              .newCreateInstanceCommand()
              .bpmnProcessId(correlationPoint.bpmnProcessId())
              .version(correlationPoint.version())
              .tenantId(activatedElement.tenantId())
              .variables(extractedVariables)
              .withResult()
              .send()
              .join();

      LOG.info(
          "Created a process instance with key {} synchronously, received result variables",
          result.getProcessInstanceKey());
      return new CorrelationResult.Success.ProcessInstanceCreatedWithResult(
          activatedElement.element(),
          result.getProcessInstanceKey(),
          result.getTenantId(),
          result.getVariablesAsMap());

    } catch (ClientStatusException e1) {
      LOG.info("Failed to create process instance with result: ", e1);
      return new CorrelationResult.Failure.ZeebeClientStatus(
          e1.getStatus().getCode().name(), e1.getMessage());
    } catch (Throwable e2) {
      return new Other(e2);
    }
  }

  protected CorrelationResult triggerMessageStartEvent(
      InboundConnectorElement activatedElement,
      MessageStartEventCorrelationPoint correlationPoint,
      Object variables,
      String messageId) {

    var correlationKey =
        extractCorrelationKey(correlationPoint.correlationKeyExpression(), variables);

    if (activatedElement.synchronousResponse()) {
      return correlateMessageSynchronously(
          activatedElement, correlationPoint.messageName(), variables, correlationKey.orElse(""));
    }

    return publishMessage(
        activatedElement,
        correlationPoint.messageName(),
        variables,
        messageId,
        correlationPoint.timeToLive(),
        correlationKey.orElse(""));
  }

  protected CorrelationResult triggerMessage(
      InboundConnectorElement activatedElement,
      MessageCorrelationPoint correlationPoint,
      Object variables,
      String messageId) {

    var correlationKeyExpression = correlationPoint.correlationKeyExpression();
    var correlationKey = extractCorrelationKey(correlationKeyExpression, variables);
    if (correlationKey.isEmpty()) {
      return new CorrelationResult.Failure.InvalidInput(
          "Wasn't able to obtain correlation key for expression " + correlationKeyExpression, null);
    }

    if (activatedElement.synchronousResponse()) {
      return correlateMessageSynchronously(
          activatedElement, correlationPoint.messageName(), variables, correlationKey.get());
    }

    return publishMessage(
        activatedElement,
        correlationPoint.messageName(),
        variables,
        messageId,
        correlationPoint.timeToLive(),
        correlationKey.get());
  }

  /**
   * Correlates a message synchronously using {@code newCorrelateMessageCommand}, waiting for the
   * message to be correlated before returning. Returns a {@link
   * CorrelationResult.Success.MessageCorrelated} with the process instance key on success.
   */
  private CorrelationResult correlateMessageSynchronously(
      InboundConnectorElement activatedElement,
      String messageName,
      Object variables,
      String correlationKey) {
    Object extractedVariables = extractVariables(variables, activatedElement);
    try {
      var step2 = camundaClient.newCorrelateMessageCommand().messageName(messageName);
      var step3 =
          correlationKey.isBlank()
              ? step2.withoutCorrelationKey()
              : step2.correlationKey(correlationKey);
      step3.variables(extractedVariables).tenantId(activatedElement.tenantId());
      CorrelateMessageResponse response = step3.send().join();

      LOG.info(
          "Correlated message synchronously, process instance key: {}",
          response.getProcessInstanceKey());
      return new CorrelationResult.Success.MessageCorrelated(
          activatedElement.element(),
          response.getProcessInstanceKey(),
          response.getMessageKey(),
          response.getTenantId());

    } catch (ClientStatusException ex) {
      LOG.info("Failed to correlate message synchronously: {}", ex.getMessage());
      return new CorrelationResult.Failure.ZeebeClientStatus(
          ex.getStatus().getCode().name(), ex.getMessage());
    } catch (Exception ex) {
      return new Failure.Other(ex);
    }
  }

  private CorrelationResult publishMessage(
      InboundConnectorElement activatedElement,
      String messageName,
      Object variables,
      String messageId,
      Duration timeToLive,
      String correlationKey) {
    Object extractedVariables = extractVariables(variables, activatedElement);
    CorrelationResult result;
    try {
      var command =
          camundaClient
              .newPublishMessageCommand()
              .messageName(messageName)
              .correlationKey(correlationKey)
              .messageId(messageId)
              .tenantId(activatedElement.tenantId())
              .variables(extractedVariables);
      if (timeToLive != null) {
        command.timeToLive(timeToLive);
      } else {
        command.timeToLive(defaultMessageTtl);
      }
      PublishMessageResponse response = command.send().join();

      LOG.info("Published message with key: {}", response.getMessageKey());
      result =
          new CorrelationResult.Success.MessagePublished(
              activatedElement.element(), response.getMessageKey(), response.getTenantId());
    } catch (ClientStatusException ex) {
      if (Status.ALREADY_EXISTS.getCode().equals(ex.getStatus().getCode())) {
        result = new MessageAlreadyCorrelated(activatedElement.element());
        LOG.debug("Message already correlated: {}", ex.getMessage());
      } else {
        LOG.info("Failed to publish message: {}", ex.getMessage());
        result =
            new CorrelationResult.Failure.ZeebeClientStatus(
                ex.getStatus().getCode().name(), ex.getMessage());
      }
    } catch (Exception ex) {
      result = new Failure.Other(ex);
    }
    return result;
  }

  private InboundConnectorElement findMatchingElement(
      List<InboundConnectorElement> elements, ProcessElement contentElement) {
    return elements.stream()
        .filter(e -> e.element().elementId().equals(contentElement.elementId()))
        .findFirst()
        .get();
  }

  public ActivationCheckResult canActivate(List<InboundConnectorElement> elements, Object context) {
    return activationConditionEvaluator.checkActivation(elements, context);
  }

  protected boolean isActivationConditionMet(InboundConnectorElement definition, Object context) {
    return activationConditionEvaluator.isActivationConditionMet(definition, context);
  }

  protected Optional<String> extractCorrelationKey(
      String correlationKeyExpression, Object context) {
    Optional<String> correlationKey;
    if (correlationKeyExpression != null && !correlationKeyExpression.isBlank()) {
      try {
        correlationKey =
            Optional.ofNullable(
                feelEngine.evaluate(correlationKeyExpression, String.class, context));
      } catch (Exception e) {
        correlationKey = Optional.empty();
      }
    } else {
      correlationKey = Optional.empty();
    }
    return correlationKey;
  }

  protected Object extractVariables(Object rawVariables, InboundConnectorElement definition) {
    return connectorResultHandler.createOutputVariables(
        rawVariables, definition.resultVariable(), definition.resultExpression());
  }

  private String resolveMessageId(String messageIdExpression, String messageId, Object context) {
    if (!Objects.isNull(messageIdExpression) && !messageIdExpression.isBlank()) {
      try {
        return feelEngine.evaluate(messageIdExpression, String.class, context);
      } catch (Exception e) {
        throw new ConnectorInputException(
            "Message expression could not be evaluated" + messageIdExpression, e);
      }
    } else if (!Objects.isNull(messageId)) {
      return messageId;
    } else {
      return "";
    }
  }
}
