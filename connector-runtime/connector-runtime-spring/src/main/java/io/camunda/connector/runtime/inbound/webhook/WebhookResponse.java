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
package io.camunda.connector.runtime.inbound.webhook;

import static io.camunda.connector.runtime.inbound.lifecycle.InboundConnectorManager.WEBHOOK_CONTEXT_BPMN_FIELD;

import io.camunda.connector.api.inbound.InboundConnectorResult;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: Define how much information we want to expose as result
public class WebhookResponse {

  private List<String> unactivatedConnectors = new ArrayList<>();
  private Map<String, InboundConnectorResult> executedConnectors = new HashMap<>();
  private List<String> errors = new ArrayList<>();

  public void addUnactivatedConnector(InboundConnectorProperties connectorProperties) {
    unactivatedConnectors.add(webhookIdentifier(connectorProperties));
  }

  public void addExecutedConnector(
      InboundConnectorProperties connectorProperties, InboundConnectorResult result) {
    executedConnectors.put(webhookIdentifier(connectorProperties), result);
  }

  public void addException(InboundConnectorProperties connectorProperties, Exception exception) {
    errors.add(webhookIdentifier(connectorProperties) + ">" + exception.getMessage());
  }

  public List<String> getUnactivatedConnectors() {
    return unactivatedConnectors;
  }

  public Map<String, InboundConnectorResult> getExecutedConnectors() {
    return executedConnectors;
  }

  public List<String> getErrors() {
    return errors;
  }

  private String webhookIdentifier(InboundConnectorProperties properties) {
    return properties.getRequiredProperty(WEBHOOK_CONTEXT_BPMN_FIELD)
        + "-"
        + properties.getBpmnProcessId()
        + "-"
        + properties.getVersion();
  }
}
