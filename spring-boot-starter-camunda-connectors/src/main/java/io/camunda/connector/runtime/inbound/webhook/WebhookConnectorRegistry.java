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

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.WebhookConnectorExecutable;
import io.camunda.connector.impl.inbound.InboundConnectorProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebhookConnectorRegistry {

  private final Logger LOG = LoggerFactory.getLogger(WebhookConnectorRegistry.class);

  // active endpoints grouped by context path (additionally indexed by correlationPointId for faster
  // lookup)
  private final Map<String, Map<String, InboundConnectorContext>> activeEndpointsByContext =
      new HashMap<>();
  private final Map<String, WebhookConnectorExecutable> webhookExecsByType = new HashMap<>();

  public boolean containsContextPath(String context) {
    return activeEndpointsByContext.containsKey(context)
        && !activeEndpointsByContext.get(context).isEmpty();
  }

  public List<InboundConnectorContext> getWebhookConnectorByContextPath(String context) {
    return new ArrayList<>(activeEndpointsByContext.get(context).values());
  }

  public void activateEndpoint(InboundConnectorContext connectorContext) {

    InboundConnectorProperties properties = connectorContext.getProperties();
    WebhookConnectorProperties webhookProperties = new WebhookConnectorProperties(properties);

    activeEndpointsByContext.compute(
        webhookProperties.getContext(),
        (context, endpoints) -> {
          if (endpoints == null) {
            Map<String, InboundConnectorContext> newEndpoints = new HashMap<>();
            newEndpoints.put(properties.getCorrelationPointId(), connectorContext);
            return newEndpoints;
          }
          endpoints.put(properties.getCorrelationPointId(), connectorContext);
          return endpoints;
        });
  }

  public void deactivateEndpoint(InboundConnectorProperties inboundConnectorProperties) {
    WebhookConnectorProperties webhookProperties =
        new WebhookConnectorProperties(inboundConnectorProperties);

    activeEndpointsByContext.compute(
        webhookProperties.getContext(),
        (context, endpoints) -> {
          if (endpoints == null
              || !endpoints.containsKey(inboundConnectorProperties.getCorrelationPointId())) {
            LOG.warn(
                "Attempted to disable non-existing webhook endpoint. "
                    + "This indicates a potential error in the connector lifecycle.");
            return endpoints;
          }
          endpoints.remove(inboundConnectorProperties.getCorrelationPointId());
          return endpoints;
        });
  }
  
  public WebhookConnectorExecutable getByType(String type) {
      return webhookExecsByType.get(type);
  }
  
  public void registerWebhookFunction(String type, WebhookConnectorExecutable function) {
      webhookExecsByType.put(type, function);
  }
}
