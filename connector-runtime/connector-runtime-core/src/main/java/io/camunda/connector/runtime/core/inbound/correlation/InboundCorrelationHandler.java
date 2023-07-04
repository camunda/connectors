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

import static io.camunda.connector.impl.Constants.ACTIVATION_CONDITION_KEYWORD;
import static io.camunda.connector.impl.Constants.CORRELATION_KEY_EXPRESSION_KEYWORD;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.ConnectorInputException;
import io.camunda.connector.impl.inbound.ProcessCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.impl.inbound.result.CorrelatedMessage;
import io.camunda.connector.impl.inbound.result.CorrelationErrorData;
import io.camunda.connector.impl.inbound.result.CorrelationErrorData.CorrelationErrorReason;
import io.camunda.connector.impl.inbound.result.MessageCorrelationResult;
import io.camunda.connector.impl.inbound.result.ProcessInstance;
import io.camunda.connector.impl.inbound.result.StartEventCorrelationResult;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapperException;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.response.PublishMessageResponse;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component responsible for calling Zeebe to report an inbound event
 */
public class InboundCorrelationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(InboundCorrelationHandler.class);

  private final ZeebeClient zeebeClient;
  private final FeelEngineWrapper feelEngine;

  public InboundCorrelationHandler(ZeebeClient zeebeClient, FeelEngineWrapper feelEngine) {
    this.zeebeClient = zeebeClient;
    this.feelEngine = feelEngine;
  }

  public InboundConnectorResult<?> correlate(Map<String, ?> properties,
      ProcessCorrelationPoint correlationPoint, Object variables) {

    if (correlationPoint instanceof StartEventCorrelationPoint startCorPoint) {
      return triggerStartEvent(properties, startCorPoint, variables);
    }
    if (correlationPoint instanceof MessageCorrelationPoint msgCorPoint) {
      return triggerMessage(properties, msgCorPoint, variables);
    }
    throw new ConnectorException(
        "Process correlation point "
            + correlationPoint.getClass()
            + " is not supported by Runtime");
  }

  protected InboundConnectorResult<ProcessInstance> triggerStartEvent(
      Map<String, ?> properties, StartEventCorrelationPoint correlationPoint,
      Object variables) {

    if (!isActivationConditionMet(properties, variables)) {
      LOG.debug("Activation condition didn't match: {}", correlationPoint);
      return new StartEventCorrelationResult(
          correlationPoint.getProcessDefinitionKey(),
          new CorrelationErrorData(CorrelationErrorReason.ACTIVATION_CONDITION_NOT_MET));
    }
    Object extractedVariables = extractVariables(variables, properties);

    try {
      ProcessInstanceEvent result =
          zeebeClient
              .newCreateInstanceCommand()
              .bpmnProcessId(correlationPoint.getBpmnProcessId())
              .version(correlationPoint.getVersion())
              .variables(extractedVariables)
              .send()
              .join();

      LOG.info("Created a process instance with key" + result.getProcessInstanceKey());
      return new StartEventCorrelationResult(
          result.getProcessDefinitionKey(),
          new ProcessInstance(
              result.getProcessInstanceKey(), correlationPoint.getBpmnProcessId(),
              correlationPoint.getProcessDefinitionKey(), correlationPoint.getVersion()));

    } catch (Exception e) {
      throw new ConnectorException(
          "Failed to start process instance via StartEvent: " + correlationPoint, e);
    }
  }

  protected InboundConnectorResult<CorrelatedMessage> triggerMessage(
      Map<String, ?> properties, MessageCorrelationPoint correlationPoint, Object variables) {

    String correlationKey = extractCorrelationKey(properties, variables);

    if (!isActivationConditionMet(properties, variables)) {
      LOG.debug("Activation condition didn't match: {}", correlationPoint);
      return new MessageCorrelationResult(
          correlationPoint.getMessageName(),
          new CorrelationErrorData(CorrelationErrorReason.ACTIVATION_CONDITION_NOT_MET));
    }

    Object extractedVariables = extractVariables(variables, properties);

    try {
      PublishMessageResponse response =
          zeebeClient
              .newPublishMessageCommand()
              .messageName(correlationPoint.getMessageName())
              .correlationKey(correlationKey)
              .variables(extractedVariables)
              .send()
              .join();

      LOG.info("Published message with key: " + response.getMessageKey());
      return new MessageCorrelationResult(
          correlationPoint.getMessageName(), response.getMessageKey());

    } catch (Exception e) {
      throw new ConnectorException(
          "Failed to publish process message for subscription: " + correlationPoint, e);
    }
  }

  protected boolean isActivationConditionMet(Map<String, ?> properties, Object context) {

    String activationCondition = getProperty(ACTIVATION_CONDITION_KEYWORD, properties);
    if (activationCondition == null || activationCondition.trim().length() == 0) {
      LOG.debug("No activation condition specified for connector");
      return true;
    }
    try {
      Object shouldActivate = feelEngine.evaluate(activationCondition, context);
      return Boolean.TRUE.equals(shouldActivate);
    } catch (FeelEngineWrapperException e) {
      throw new ConnectorInputException(e);
    }
  }

  protected String extractCorrelationKey(Map<String, ?> properties, Object context) {
    String correlationKeyExpression =
        getRequiredProperty(CORRELATION_KEY_EXPRESSION_KEYWORD, properties);
    try {
      return feelEngine.evaluate(correlationKeyExpression, context);
    } catch (Exception e) {
      throw new ConnectorInputException(e);
    }
  }

  protected Object extractVariables(Object rawVariables, Map<String, ?> properties) {
    return ConnectorHelper.createOutputVariables(rawVariables, properties);
  }

  protected String getRequiredProperty(String key, Map<String, ?> properties) {
    String result = getProperty(key, properties);
    if (result == null) {
      throw new IllegalArgumentException("Required property '" + key + "' not found");
    }
    return result;
  }

  protected String getProperty(String key, Map<String, ?> properties) {
    Object result = properties.get(key);
    if (result == null || result instanceof String) {
      return (String) result;
    }
    throw new IllegalArgumentException("Property '" + key + "' is malformed");
  }
}
