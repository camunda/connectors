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
import io.camunda.connector.api.inbound.CorrelationErrorData;
import io.camunda.connector.api.inbound.CorrelationErrorData.CorrelationErrorReason;
import io.camunda.connector.api.inbound.CorrelationResult;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.inbound.InboundConnectorDefinitionImpl;
import io.camunda.connector.runtime.core.inbound.result.CorrelatedMessage;
import io.camunda.connector.runtime.core.inbound.result.CorrelatedMessageStart;
import io.camunda.connector.runtime.core.inbound.result.MessageCorrelationResult;
import io.camunda.connector.runtime.core.inbound.result.MessageStartCorrelationResult;
import io.camunda.connector.runtime.core.inbound.result.ProcessInstance;
import io.camunda.connector.runtime.core.inbound.result.StartEventCorrelationResult;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ClientStatusException;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
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

  public CorrelationResult<?> correlate(
      InboundConnectorDefinitionImpl definition, Object variables) {
    return correlate(definition, variables, UUID.randomUUID().toString());
  }

  public CorrelationResult<?> correlate(
      InboundConnectorDefinitionImpl definition, Object variables, String messageId) {

    var correlationPoint = definition.correlationPoint();

    if (correlationPoint instanceof StartEventCorrelationPoint startCorPoint) {
      return triggerStartEvent(definition, startCorPoint, variables);
    }
    if (correlationPoint instanceof MessageCorrelationPoint msgCorPoint) {
      return triggerMessage(
          definition,
          msgCorPoint.messageName(),
          msgCorPoint.correlationKeyExpression(),
          variables,
          messageId);
    }
    if (correlationPoint instanceof MessageStartEventCorrelationPoint msgStartCorPoint) {
      return triggerMessageStartEvent(definition, msgStartCorPoint, variables);
    }
    if (correlationPoint instanceof BoundaryEventCorrelationPoint boundaryEventCorrelationPoint) {
      return triggerMessage(
          definition,
          boundaryEventCorrelationPoint.messageName(),
          boundaryEventCorrelationPoint.correlationKeyExpression(),
          variables,
          boundaryEventCorrelationPoint.messageIdExpression());
    }
    throw new ConnectorException(
        "Process correlation point "
            + correlationPoint.getClass()
            + " is not supported by Runtime");
  }

  protected CorrelationResult<ProcessInstance> triggerStartEvent(
      InboundConnectorDefinitionImpl definition,
      StartEventCorrelationPoint correlationPoint,
      Object variables) {

    if (!isActivationConditionMet(definition, variables)) {
      LOG.debug("Activation condition didn't match: {}", correlationPoint);
      return new StartEventCorrelationResult(
          correlationPoint.processDefinitionKey(),
          new CorrelationErrorData(CorrelationErrorReason.ACTIVATION_CONDITION_NOT_MET));
    }
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
      return new StartEventCorrelationResult(
          result.getProcessDefinitionKey(),
          new ProcessInstance(
              result.getProcessInstanceKey(), correlationPoint.bpmnProcessId(),
              correlationPoint.processDefinitionKey(), correlationPoint.version()));

    } catch (Exception e) {
      throw new ConnectorException(
          "Failed to start process instance via StartEvent: " + correlationPoint, e);
    }
  }

  protected CorrelationResult<CorrelatedMessageStart> triggerMessageStartEvent(
      InboundConnectorDefinitionImpl definition,
      MessageStartEventCorrelationPoint correlationPoint,
      Object variables) {

    if (!isActivationConditionMet(definition, variables)) {
      LOG.debug("Activation condition didn't match: {}", correlationPoint);
      return new MessageStartCorrelationResult(
          correlationPoint.messageName(),
          new CorrelationErrorData(CorrelationErrorReason.ACTIVATION_CONDITION_NOT_MET));
    }

    String messageId = extractMessageKey(correlationPoint, variables);
    if (correlationPoint.messageIdExpression() != null
        && !correlationPoint.messageIdExpression().isBlank()
        && messageId == null) {
      LOG.debug(
          "Wasn't able to obtain idempotency key for expression {}.",
          correlationPoint.messageIdExpression());
      return new MessageStartCorrelationResult(
          correlationPoint.messageName(),
          new CorrelationErrorData(CorrelationErrorReason.FAULT_IDEMPOTENCY_KEY));
    }

    Object extractedVariables = extractVariables(variables, definition);

    try {
      String correlationKey =
          extractCorrelationKey(correlationPoint.correlationKeyExpression(), variables);
      PublishMessageResponse result =
          zeebeClient
              .newPublishMessageCommand()
              .messageName(correlationPoint.messageName())
              // correlation key must be empty to start a new process, see:
              // https://docs.camunda.io/docs/components/modeler/bpmn/message-events/#message-start-events
              .correlationKey(correlationKey)
              .messageId(messageId)
              .tenantId(definition.tenantId())
              .variables(extractedVariables)
              .send()
              .join();

      LOG.info("Published message with key: " + result.getMessageKey());

      return new MessageStartCorrelationResult(
          correlationPoint.messageName(),
          new CorrelatedMessageStart(
              result.getMessageKey(),
              messageId,
              correlationPoint.bpmnProcessId(),
              correlationPoint.processDefinitionKey(),
              correlationPoint.version()));

    } catch (ClientStatusException e1) {
      // gracefully handle zeebe rejections, such as idempotency key rejection
      LOG.info("Failed to publish message: ", e1);
      return new MessageStartCorrelationResult(
          correlationPoint.messageName(),
          new CorrelationErrorData(
              CorrelationErrorReason.FAULT_ZEEBE_CLIENT_STATUS, e1.getMessage()));
    } catch (Exception e2) {
      throw new ConnectorException(
          "Failed to publish process message for subscription: " + correlationPoint, e2);
    }
  }

  protected CorrelationResult<CorrelatedMessage> triggerMessage(
      InboundConnectorDefinitionImpl definition,
      String messageName,
      String correlationKeyExpression,
      Object variables,
      String messageId) {

    String correlationKey = extractCorrelationKey(correlationKeyExpression, variables);

    if (!isActivationConditionMet(definition, variables)) {
      LOG.debug("Activation condition didn't match: {}", definition.correlationPoint());
      return new MessageCorrelationResult(
          messageName,
          new CorrelationErrorData(CorrelationErrorReason.ACTIVATION_CONDITION_NOT_MET));
    }

    Object extractedVariables = extractVariables(variables, definition);

    try {
      PublishMessageResponse response =
          zeebeClient
              .newPublishMessageCommand()
              .messageName(messageName)
              .correlationKey(correlationKey)
              .messageId(messageId)
              .tenantId(definition.tenantId())
              .variables(extractedVariables)
              .send()
              .join();

      LOG.info("Published message with key: " + response.getMessageKey());
      return new MessageCorrelationResult(messageName, response.getMessageKey());

    } catch (Exception e) {
      throw new ConnectorException(
          "Failed to publish process message for subscription: " + definition.correlationPoint(),
          e);
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

  protected String extractCorrelationKey(String correlationKeyExpression, Object context) {
    if (correlationKeyExpression == null || correlationKeyExpression.isBlank()) {
      return "";
    }
    try {
      return feelEngine.evaluate(correlationKeyExpression, context, String.class);
    } catch (Exception e) {
      throw new ConnectorInputException(e);
    }
  }

  protected String extractMessageKey(MessageStartEventCorrelationPoint point, Object context) {
    final String messageIdExpression = point.messageIdExpression();
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
}
