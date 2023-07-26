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
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.api.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.api.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.api.inbound.result.CorrelatedMessage;
import io.camunda.connector.api.inbound.result.CorrelationErrorData;
import io.camunda.connector.api.inbound.result.CorrelationErrorData.CorrelationErrorReason;
import io.camunda.connector.api.inbound.result.MessageCorrelationResult;
import io.camunda.connector.api.inbound.result.ProcessInstance;
import io.camunda.connector.api.inbound.result.StartEventCorrelationResult;
import io.camunda.connector.feel.FeelEngineWrapper;
import io.camunda.connector.feel.FeelEngineWrapperException;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.inbound.InboundConnectorDefinitionImpl;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
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

  public InboundConnectorResult<?> correlate(
      InboundConnectorDefinitionImpl definition, Object variables) {
    var correlationPoint = definition.correlationPoint();

    if (correlationPoint instanceof StartEventCorrelationPoint startCorPoint) {
      return triggerStartEvent(definition, startCorPoint, variables);
    }
    if (correlationPoint instanceof MessageCorrelationPoint msgCorPoint) {
      return triggerMessage(definition, msgCorPoint, variables);
    }
    throw new ConnectorException(
        "Process correlation point "
            + correlationPoint.getClass()
            + " is not supported by Runtime");
  }

  protected InboundConnectorResult<ProcessInstance> triggerStartEvent(
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

  protected InboundConnectorResult<CorrelatedMessage> triggerMessage(
      InboundConnectorDefinitionImpl definition,
      MessageCorrelationPoint correlationPoint,
      Object variables) {

    String correlationKey = extractCorrelationKey(correlationPoint, variables);

    if (!isActivationConditionMet(definition, variables)) {
      LOG.debug("Activation condition didn't match: {}", correlationPoint);
      return new MessageCorrelationResult(
          correlationPoint.messageName(),
          new CorrelationErrorData(CorrelationErrorReason.ACTIVATION_CONDITION_NOT_MET));
    }

    Object extractedVariables = extractVariables(variables, definition);

    try {
      PublishMessageResponse response =
          zeebeClient
              .newPublishMessageCommand()
              .messageName(correlationPoint.messageName())
              .correlationKey(correlationKey)
              .variables(extractedVariables)
              .send()
              .join();

      LOG.info("Published message with key: " + response.getMessageKey());
      return new MessageCorrelationResult(correlationPoint.messageName(), response.getMessageKey());

    } catch (Exception e) {
      throw new ConnectorException(
          "Failed to publish process message for subscription: " + correlationPoint, e);
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

  protected String extractCorrelationKey(MessageCorrelationPoint point, Object context) {
    String correlationKeyExpression = point.correlationKeyExpression();
    try {
      return feelEngine.evaluate(correlationKeyExpression, context);
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
