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

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.api.inbound.CorrelationResult.ErrorCode;
import io.camunda.connector.api.inbound.CorrelationResult.Failure;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.inbound.InboundConnectorDefinitionImpl;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import io.grpc.Status;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Component responsible for calling Zeebe to report an inbound event */
public class InboundCorrelationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(InboundCorrelationHandler.class);

  private final ZeebeClient zeebeClient;
  private final FeelEngineWrapper feelEngine;

  public InboundCorrelationHandler(ZeebeClient zeebeClient, FeelEngineWrapper feelEngine) {
    this.zeebeClient = zeebeClient;
    this.feelEngine = feelEngine;
  }

  public CorrelationResult correlate(InboundConnectorDefinitionImpl definition, Object variables) {
    return correlate(definition, variables, null);
  }

  public CorrelationResult correlate(
      InboundConnectorDefinitionImpl definition, Object variables, String messageId) {

    var correlationPoint = definition.correlationPoint();

    try {
      if (!isActivationConditionMet(definition, variables)) {
        LOG.info("Activation condition didn't match: {}", correlationPoint);
        return new CorrelationResult.Failure(ErrorCode.ACTIVATION_CONDITION_NOT_MET, null, null);
      }
    } catch (ConnectorInputException e) {
      LOG.info("Failed to evaluate activation condition: {}", correlationPoint);
      return new CorrelationResult.Failure(
          ErrorCode.INVALID_INPUT,
          "Failed to evaluate activation condition against the provided input",
          e);
    }

    if (correlationPoint instanceof StartEventCorrelationPoint startCorPoint) {
      return triggerStartEvent(definition, startCorPoint, variables);
    } else if (correlationPoint instanceof MessageCorrelationPoint msgCorPoint) {
      return triggerMessage(
          definition,
          msgCorPoint.messageName(),
          msgCorPoint.correlationKeyExpression(),
          variables,
          resolveMessageId(msgCorPoint.messageIdExpression(), messageId, variables));
    } else if (correlationPoint instanceof MessageStartEventCorrelationPoint msgStartCorPoint) {
      return triggerMessageStartEvent(definition, msgStartCorPoint, variables);
    } else if (correlationPoint
        instanceof BoundaryEventCorrelationPoint boundaryEventCorrelationPoint) {
      return triggerMessage(
          definition,
          boundaryEventCorrelationPoint.messageName(),
          boundaryEventCorrelationPoint.correlationKeyExpression(),
          variables,
          resolveMessageId(
              boundaryEventCorrelationPoint.messageIdExpression(), messageId, variables));
    } else {
      // this should never happen, thus not wrapped in a CorrelationResult
      throw new ConnectorException(
          "Process correlation point "
              + correlationPoint.getClass()
              + " is not supported by Runtime");
    }
  }

  protected CorrelationResult triggerStartEvent(
      InboundConnectorDefinitionImpl definition,
      StartEventCorrelationPoint correlationPoint,
      Object variables) {

    Object extractedVariables = extractVariables(variables, definition);

    try {
      ProcessInstanceEvent result =
          zeebeClient
              .newCreateInstanceCommand()
              .bpmnProcessId(correlationPoint.bpmnProcessId())
              .version(correlationPoint.version())
              .tenantId(definition.tenantId())
              .variables(extractedVariables)
              .send()
              .join();

      LOG.info("Created a process instance with key" + result.getProcessInstanceKey());
      return CorrelationResult.Success.INSTANCE;

    } catch (ClientStatusException e1) {
      LOG.info("Failed to publish message: ", e1);
      return new CorrelationResult.Failure(ErrorCode.ZEEBE_CLIENT_STATUS, null, e1);
    } catch (Exception e2) {
      return new Failure(ErrorCode.UNKNOWN, e2.getMessage(), e2);
    }
  }

  protected CorrelationResult triggerMessageStartEvent(
      InboundConnectorDefinitionImpl definition,
      MessageStartEventCorrelationPoint correlationPoint,
      Object variables) {

    String messageId = extractMessageId(correlationPoint.messageIdExpression(), variables);
    if (correlationPoint.messageIdExpression() != null
        && !correlationPoint.messageIdExpression().isBlank()
        && messageId == null) {
      LOG.debug(
          "Wasn't able to obtain idempotency key for expression {}.",
          correlationPoint.messageIdExpression());
      return new CorrelationResult.Failure(
          ErrorCode.INVALID_INPUT,
          "Wasn't able to obtain idempotency key for expression "
              + correlationPoint.messageIdExpression(),
          null);
    }

    Object extractedVariables = extractVariables(variables, definition);

    try {
      var correlationKey =
          extractCorrelationKey(correlationPoint.correlationKeyExpression(), variables);
      PublishMessageResponse result =
          zeebeClient
              .newPublishMessageCommand()
              .messageName(correlationPoint.messageName())
              // correlation key must be empty to start a new process, see:
              // https://docs.camunda.io/docs/components/modeler/bpmn/message-events/#message-start-events
              .correlationKey(correlationKey.orElse(""))
              .messageId(messageId)
              .tenantId(definition.tenantId())
              .variables(extractedVariables)
              .send()
              .join();

      LOG.info("Published message with key: " + result.getMessageKey());
      return CorrelationResult.Success.INSTANCE;

    } catch (ClientStatusException e1) {
      LOG.info("Failed to publish message: ", e1);
      if (Status.ALREADY_EXISTS.equals(e1.getStatus())) {
        return new CorrelationResult.Failure(
            ErrorCode.MESSAGE_ALREADY_CORRELATED,
            "Message with idempotency key "
                + messageId
                + " already exists. Duplicate message was rejected by Zeebe.",
            e1);
      }
      return new CorrelationResult.Failure(ErrorCode.ZEEBE_CLIENT_STATUS, null, e1);
    }
  }

  protected CorrelationResult triggerMessage(
      InboundConnectorDefinitionImpl definition,
      String messageName,
      String correlationKeyExpression,
      Object variables,
      String messageId) {

    var correlationKey = extractCorrelationKey(correlationKeyExpression, variables);
    if (correlationKey.isEmpty()) {
      return new CorrelationResult.Failure(
          ErrorCode.INVALID_INPUT,
          "Wasn't able to obtain correlation key for expression " + correlationKeyExpression,
          null);
    }

    Object extractedVariables = extractVariables(variables, definition);
    try {
      PublishMessageResponse response =
          zeebeClient
              .newPublishMessageCommand()
              .messageName(messageName)
              .correlationKey(correlationKey.get())
              .messageId(messageId)
              .tenantId(definition.tenantId())
              .variables(extractedVariables)
              .send()
              .join();

      LOG.info("Published message with key: " + response.getMessageKey());
      return CorrelationResult.Success.INSTANCE;

    } catch (ClientStatusException e1) {
      LOG.info("Failed to publish message: ", e1);
      if (Status.ALREADY_EXISTS.equals(e1.getStatus())) {
        return new CorrelationResult.Failure(
            ErrorCode.MESSAGE_ALREADY_CORRELATED,
            "Message with idempotency key "
                + messageId
                + " already exists. Duplicate message was rejected by Zeebe.",
            e1);
      }
      return new CorrelationResult.Failure(ErrorCode.ZEEBE_CLIENT_STATUS, null, e1);
    } catch (Exception e2) {
      return new Failure(ErrorCode.UNKNOWN, e2.getMessage(), e2);
    }
  }

  protected boolean isActivationConditionMet(
      InboundConnectorDefinitionImpl definition, Object context) {

    var maybeCondition = definition.activationCondition();
    if (maybeCondition == null || maybeCondition.isBlank()) {
      LOG.debug("No activation condition specified for connector");
      return true;
    }
    try {
      Object shouldActivate = feelEngine.evaluate(maybeCondition, context);
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
                feelEngine.evaluate(correlationKeyExpression, context, String.class));
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
      return feelEngine.evaluate(messageIdExpression, context, String.class);
    } catch (Exception e) {
      throw new ConnectorInputException(e);
    }
  }

  protected Object extractVariables(
      Object rawVariables, InboundConnectorDefinitionImpl definition) {
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
}
