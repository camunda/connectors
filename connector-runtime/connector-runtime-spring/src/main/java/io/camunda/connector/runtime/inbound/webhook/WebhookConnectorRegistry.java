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

import io.camunda.connector.runtime.inbound.lifecycle.ActiveInboundConnector;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebhookConnectorRegistry {

  private final Logger LOG = LoggerFactory.getLogger(WebhookConnectorRegistry.class);

  private final Map<String, ActiveInboundConnector> activeEndpointsByContext = new HashMap<>();

  public Optional<ActiveInboundConnector> getWebhookConnectorByContextPath(String context) {
    return Optional.ofNullable(activeEndpointsByContext.get(context));
  }

  public void register(ActiveInboundConnector connector) {
    var properties = connector.context().getProperties();
    var context = properties.get(WEBHOOK_CONTEXT_BPMN_FIELD).toString();
    var existingEndpoint = activeEndpointsByContext.putIfAbsent(context, connector);
    if (existingEndpoint != null) {
      var bpmnProcessId = existingEndpoint.context().getDefinition().bpmnProcessId();
      var elementId = existingEndpoint.context().getDefinition().elementId();
      var logMessage =
          "Context: " + context + " already in use by " + bpmnProcessId + "/" + elementId + ".";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }
  }

  public void deregister(ActiveInboundConnector connector) {
    var context = connector.context().getProperties().get(WEBHOOK_CONTEXT_BPMN_FIELD).toString();
    activeEndpointsByContext.remove(context);
  }

  public void reset() {
    activeEndpointsByContext.clear();
  }
}
