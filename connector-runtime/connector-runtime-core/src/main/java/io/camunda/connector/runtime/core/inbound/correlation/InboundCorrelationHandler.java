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

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.ActivationConditionNotMet;
import io.camunda.connector.api.inbound.CorrelationResult.Failure.Other;
import io.camunda.connector.api.inbound.CorrelationResult.Success.MessageAlreadyCorrelated;
import io.camunda.connector.api.inbound.ProcessElementContext;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.core.inbound.ProcessElementContextFactory;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.grpc.Status;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Component responsible for calling Zeebe to report an inbound event */
public class InboundCorrelationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(InboundCorrelationHandler.class);

  private final ZeebeClient zeebeClient;
  private final FeelEngineWrapper feelEngine;

  private final ProcessElementContextFactory processElementContextFactory;

  public InboundCorrelationHandler(
      ZeebeClient zeebeClient,
      FeelEngineWrapper feelEngine,
      ProcessElementContextFactory processElementContextFactory) {
    this.zeebeClient = zeebeClient;
    this.feelEngine = feelEngine;
    this.processElementContextFactory = processElementContextFactory;
  }

  public CorrelationResult correlate(List<InboundConnectorElement> elements, Object variables) {
    return correlate(elements, variables, null);
  }

  public CorrelationResult correlate(
      List<InboundConnectorElement> elements, Object variables, String messageId) {

    final List<InboundConnectorElement> matchingElements;
    try {
      matchingElements =
          elements.stream().filter(e -> isActivationConditionMet(e, variables)).toList();
    } catch (ConnectorInputException e) {
      LOG.info("Failed to evaluate activation condition", e);
      return new CorrelationResult.Failure.InvalidInput(
          "Failed to evaluate activation condition against the provided input", e);
    }

    if (matchingElements.isEmpty()) {
      return ActivationConditionNotMet.INSTANCE;
    }
    if (matchingElements.size() > 1) {
      return new Failure.InvalidInput("Multiple connectors are activated for the same input", null);
    }

    var activatedElement = matchingElements.getFirst();
    return correlateInternal(activatedElement, variables, messageId);
  }

  protected CorrelationResult correlateInternal(
      InboundConnectorElement activatedElement, Object variables, String messageId) {
    var correlationPoint = activatedElement.correlationPoint();

    return switch (correlationPoint) {
      case StartEventCorrelationPoint corPoint -> triggerStartEvent(
          activatedElement, corPoint, variables);
      case MessageCorrelationPoint corPoint -> triggerMessage(
          activatedElement,
          corPoint,
          variables,
          resolveMessageId(corPoint.messageIdExpression(), messageId, variables));
      case MessageStartEventCorrelationPoint corPoint -> triggerMessageStartEvent(
          activatedElement, corPoint, variables);
    };
  }

  protected CorrelationResult triggerStartEvent(
      InboundConnectorElement activatedElement,
      StartEventCorrelationPoint correlationPoint,
      Object variables) {

    Object extractedVariables = extractVariables(variables, activatedElement);

    try {
      ProcessInstanceEvent result =
          zeebeClient
              .newCreateInstanceCommand()
              .bpmnProcessId(correlationPoint.bpmnProcessId())
              .version(correlationPoint.version())
              .tenantId(activatedElement.tenantId())
              .variables(extractedVariables)
              .send()
              .join();

      LOG.info("Created a process instance with key {}", result.getProcessInstanceKey());
      return new CorrelationResult.Success.ProcessInstanceCreated(
          getElementContext(activatedElement),
          result.getProcessInstanceKey(),
          result.getTenantId());

    } catch (ClientStatusException e1) {
      LOG.info("Failed to publish message: ", e1);
      return new CorrelationResult.Failure.ZeebeClientStatus(
          e1.getStatus().getCode().name(), e1.getMessage());
    } catch (Throwable e2) {
      return new Other(e2);
    }
  }

  protected CorrelationResult triggerMessageStartEvent(
      InboundConnectorElement activatedElement,
      MessageStartEventCorrelationPoint correlationPoint,
      Object variables) {

    String messageId = extractMessageId(correlationPoint.messageIdExpression(), variables);

    if (correlationPoint.messageIdExpression() != null
        && !correlationPoint.messageIdExpression().isBlank()
        && messageId == null) {
      LOG.debug(
          "Wasn't able to obtain idempotency key for expression {}.",
          correlationPoint.messageIdExpression());
      return new CorrelationResult.Failure.InvalidInput(
          "Wasn't able to obtain idempotency key for expression "
              + correlationPoint.messageIdExpression(),
          null);
    }
    var correlationKey =
        extractCorrelationKey(correlationPoint.correlationKeyExpression(), variables);
    return publishMessage(
        activatedElement,
        correlationPoint.messageName(),
        variables,
        messageId,
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

    return publishMessage(
        activatedElement,
        correlationPoint.messageName(),
        variables,
        messageId,
        correlationKey.get());
  }

  private CorrelationResult publishMessage(
      InboundConnectorElement activatedElement,
      String messageName,
      Object variables,
      String messageId,
      String correlationKey) {
    Object extractedVariables = extractVariables(variables, activatedElement);
    CorrelationResult result;
    try {
      PublishMessageResponse response =
          zeebeClient
              .newPublishMessageCommand()
              .messageName(messageName)
              .correlationKey(correlationKey)
              .messageId(messageId)
              .tenantId(activatedElement.tenantId())
              .variables(extractedVariables)
              .send()
              .join();

      LOG.info("Published message with key: {}", response.getMessageKey());
      result =
          new CorrelationResult.Success.MessagePublished(
              getElementContext(activatedElement),
              response.getMessageKey(),
              response.getTenantId());
    } catch (ClientStatusException ex) {
      if (Status.ALREADY_EXISTS.getCode().equals(ex.getStatus().getCode())) {
        result = new MessageAlreadyCorrelated(getElementContext(activatedElement));
      } else {
        LOG.info("Failed to publish message: ", ex);
        result =
            new CorrelationResult.Failure.ZeebeClientStatus(
                ex.getStatus().getCode().name(), ex.getMessage());
      }
    } catch (Exception ex) {
      result = new Failure.Other(ex);
    }
    return result;
  }

  protected boolean isActivationConditionMet(InboundConnectorElement definition, Object context) {

    var maybeCondition = definition.activationCondition();
    if (maybeCondition == null || maybeCondition.isBlank()) {
      LOG.debug("No activation condition specified for connector");
      return true;
    }
    LOG.debug("Evaluating activation condition: {}", maybeCondition);
    try {
      Object shouldActivate = feelEngine.evaluate(maybeCondition, context);
      LOG.debug("Activation condition evaluated to: {}", shouldActivate);
      return Boolean.TRUE.equals(shouldActivate);
    } catch (FeelEngineWrapperException e) {
      throw new ConnectorInputException(e);
    }
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

  protected String extractMessageId(String messageIdExpression, Object context) {
    if (messageIdExpression == null || messageIdExpression.isBlank()) {
      return "";
    }
    try {
      return feelEngine.evaluate(messageIdExpression, String.class, context);
    } catch (Exception e) {
      throw new ConnectorInputException(e);
    }
  }

  protected Object extractVariables(Object rawVariables, InboundConnectorElement definition) {
    return ConnectorHelper.createOutputVariables(
        rawVariables, definition.resultVariable(), definition.resultExpression());
  }

  private String resolveMessageId(String messageIdExpression, String messageId, Object context) {
    if (messageId == null) {
      if (messageIdExpression != null) {
        return extractMessageId(messageIdExpression, context);
      } else {
        return UUID.randomUUID().toString();
      }
    }
    return messageId;
  }

  private ProcessElementContext getElementContext(InboundConnectorElement element) {
    return processElementContextFactory.createContext(element);
  }
}
