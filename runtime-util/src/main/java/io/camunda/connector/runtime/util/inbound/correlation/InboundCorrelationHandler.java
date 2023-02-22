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
package io.camunda.connector.runtime.util.inbound.correlation;

import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.api.inbound.ProcessCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.MessageCorrelationPoint;
import io.camunda.connector.impl.inbound.correlation.StartEventCorrelationPoint;
import io.camunda.connector.runtime.util.feel.FeelEngineWrapper;
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

  public InboundConnectorResult correlate(
      ProcessCorrelationPoint correlationPoint, Object variables) {

    if (correlationPoint instanceof StartEventCorrelationPoint) {
      return triggerStartEvent((StartEventCorrelationPoint) correlationPoint, variables);
    }
    if (correlationPoint instanceof MessageCorrelationPoint) {
      return triggerMessage((MessageCorrelationPoint) correlationPoint, variables);
    }
    throw new ConnectorException(
        "Process correlation point "
            + correlationPoint.getClass()
            + " is not supported by Runtime");
  }

  private InboundConnectorResult triggerStartEvent(
      StartEventCorrelationPoint correlationPoint, Object variables) {
    try {
      ProcessInstanceEvent result =
          zeebeClient
              .newCreateInstanceCommand()
              .bpmnProcessId(correlationPoint.getBpmnProcessId())
              .version(correlationPoint.getVersion())
              .variables(variables)
              .send()
              .join();

      LOG.info("Created a process instance with key" + result.getProcessInstanceKey());
      return new StartEventInboundConnectorResult(result);

    } catch (Exception e) {
      throw new ConnectorException(
          "Failed to start process instance via StartEvent: " + correlationPoint, e);
    }
  }

  private InboundConnectorResult triggerMessage(
      MessageCorrelationPoint correlationPoint, Object variables) {

    String correlationKey =
        feelEngine.evaluate(correlationPoint.getCorrelationKeyExpression(), variables);

    try {
      PublishMessageResponse response =
          zeebeClient
              .newPublishMessageCommand()
              .messageName(correlationPoint.getMessageName())
              .correlationKey(correlationKey)
              .variables(variables)
              .send()
              .join();

      LOG.info("Published message with key: " + response.getMessageKey());
      return new MessageInboundConnectorResult(response, correlationKey);

    } catch (Exception e) {
      throw new ConnectorException(
          "Failed to publish process message for subscription: " + correlationPoint, e);
    }
  }
}
