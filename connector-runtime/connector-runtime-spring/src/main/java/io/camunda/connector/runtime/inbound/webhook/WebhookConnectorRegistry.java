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

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.runtime.inbound.lifecycle.ActiveInboundConnector;
import io.camunda.connector.runtime.inbound.webhook.model.CommonWebhookProperties;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebhookConnectorRegistry {

  private static final String WARNING_TAG = "Warning";

  // Reflect changes to this pattern in webhook element templates
  private static final Pattern CURRENT_WEBHOOK_PATH_PATTERN =
      Pattern.compile("^[a-zA-Z0-9]([a-zA-Z0-9_-]*[a-zA-Z0-9])?$");
  private static final String DEPRECATED_WEBHOOK_MESSAGE_PREFIX = "Deprecated webhook path: ";
  private static final String DEPRECATED_WEBHOOK_MESSAGE_SUFFIX =
      ". This may lead to unexpected behavior. Consider adjusting the path to match the pattern: "
          + CURRENT_WEBHOOK_PATH_PATTERN;

  private final Logger LOG = LoggerFactory.getLogger(WebhookConnectorRegistry.class);

  private final Map<String, ActiveInboundConnector> activeEndpointsByContext = new HashMap<>();

  public Optional<ActiveInboundConnector> getWebhookConnectorByContextPath(String context) {
    return Optional.ofNullable(activeEndpointsByContext.get(context));
  }

  public boolean isRegistered(ActiveInboundConnector connector) {
    var context = connector.context().bindProperties(CommonWebhookProperties.class).getContext();
    return activeEndpointsByContext.containsKey(context)
        && activeEndpointsByContext.get(context) == connector;
  }

  public void register(ActiveInboundConnector connector) {
    var properties = connector.context().bindProperties(CommonWebhookProperties.class);
    var context = properties.getContext();

    logIfWebhookPathDeprecated(connector, context);

    var existingEndpoint = activeEndpointsByContext.putIfAbsent(context, connector);
    checkIfEndpointExists(existingEndpoint, context);
  }

  private void checkIfEndpointExists(ActiveInboundConnector existingEndpoint, String context) {
    if (existingEndpoint != null) {
      var bpmnProcessId = existingEndpoint.context().getDefinition().bpmnProcessId();
      var elementId = existingEndpoint.context().getDefinition().elementId();
      var logMessage =
          "Context: " + context + " already in use by " + bpmnProcessId + "/" + elementId + ".";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }
  }

  private static void logIfWebhookPathDeprecated(ActiveInboundConnector connector, String webhook) {

    if (!CURRENT_WEBHOOK_PATH_PATTERN.matcher(webhook).matches()) {
      String message =
          DEPRECATED_WEBHOOK_MESSAGE_PREFIX + webhook + DEPRECATED_WEBHOOK_MESSAGE_SUFFIX;
      Activity activity = Activity.level(Severity.WARNING).tag(WARNING_TAG).message(message);

      connector.context().log(activity);
    }
  }

  public void deregister(ActiveInboundConnector connector) {
    var context = connector.context().bindProperties(CommonWebhookProperties.class).getContext();
    var registeredConnector = activeEndpointsByContext.get(context);
    if (registeredConnector == null) {
      var logMessage = "Context: " + context + " is not registered. Cannot deregister.";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }
    if (registeredConnector != connector) {
      var bpmnProcessId = registeredConnector.context().getDefinition().bpmnProcessId();
      var elementId = registeredConnector.context().getDefinition().elementId();
      var logMessage =
          "Context: "
              + context
              + " is not registered by "
              + bpmnProcessId
              + "/"
              + elementId
              + ". Cannot deregister.";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }
    activeEndpointsByContext.remove(context);
  }

  public void reset() {
    activeEndpointsByContext.clear();
  }
}
