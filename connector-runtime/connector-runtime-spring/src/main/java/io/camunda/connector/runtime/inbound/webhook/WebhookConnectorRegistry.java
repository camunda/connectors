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

import io.camunda.connector.runtime.inbound.executable.RegisteredExecutable;
import io.camunda.connector.runtime.inbound.webhook.model.CommonWebhookProperties;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebhookConnectorRegistry {

  private final Logger LOG = LoggerFactory.getLogger(WebhookConnectorRegistry.class);

  private final Map<String, RegisteredExecutable.Activated> activeEndpointsByContext =
      new HashMap<>();

  private final WebhookWaitingQueue waitingQueue = new WebhookWaitingQueue();

  public Optional<RegisteredExecutable.Activated> getWebhookConnectorByContextPath(String context) {
    return Optional.ofNullable(activeEndpointsByContext.get(context));
  }

  public boolean isRegistered(RegisteredExecutable.Activated connector) {
    var context = connector.context().bindProperties(CommonWebhookProperties.class).getContext();
    return activeEndpointsByContext.containsKey(context)
        && activeEndpointsByContext.get(context) == connector;
  }

  public void register(RegisteredExecutable.Activated connector) {
    var properties = connector.context().bindProperties(CommonWebhookProperties.class);
    var context = properties.getContext();
    if (context == null) {
      var logMessage = "Webhook path not provided";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }

    WebhookConnectorValidationUtil.logIfWebhookPathDeprecated(connector, context);
    Optional.ofNullable(activeEndpointsByContext.putIfAbsent(context, connector))
        .ifPresent(
            existingExecutable ->
                waitingQueue.markAsDownAndAdd(context, connector, existingExecutable));
  }

  public void deregister(RegisteredExecutable.Activated connector) {
    var context = connector.context().bindProperties(CommonWebhookProperties.class).getContext();
    var registeredConnector = activeEndpointsByContext.get(context);
    if (registeredConnector == null) {
      var logMessage = "Context: " + context + " is not registered. Cannot deregister.";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }
    var requesterDeduplicationId = connector.context().getDefinition().deduplicationId();
    var registeredDeduplicationId = registeredConnector.context().getDefinition().deduplicationId();

    if (!registeredDeduplicationId.equals(requesterDeduplicationId)) {
      var logMessage =
          "Context: "
              + context
              + " is not registered by the connector with deduplication ID: "
              + requesterDeduplicationId
              + ". Cannot deregister.";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }
    var nextConnector = waitingQueue.activateNext(context);
    if (nextConnector.isPresent()) {
      LOG.debug(
          "Next enqueued connector activated for context: {}, replacing the current one with: {}",
          context,
          nextConnector.get().context().getDefinition());
      activeEndpointsByContext.put(context, nextConnector.get());
    } else {
      LOG.debug(
          "No connectors found in the queue for context: {}, the context will be removed", context);
      activeEndpointsByContext.remove(context);
    }
  }

  public void reset() {
    activeEndpointsByContext.clear();
  }
}
