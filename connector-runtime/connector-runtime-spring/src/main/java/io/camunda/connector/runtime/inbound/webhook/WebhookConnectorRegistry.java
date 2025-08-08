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

  private final Map<String, WebhookExecutables> executablesByContext = new HashMap<>();

  public Optional<RegisteredExecutable.Activated> getActiveWebhook(String context) {
    return Optional.ofNullable(executablesByContext.get(context))
        .map(WebhookExecutables::getActiveWebhook);
  }

  public Map<String, WebhookExecutables> getExecutablesByContext() {
    return executablesByContext;
  }

  public void register(RegisteredExecutable.Activated connector) {
    var context = getContext(connector);

    WebhookConnectorValidationUtil.logIfWebhookPathDeprecated(connector, context);
    Optional.ofNullable(
            executablesByContext.putIfAbsent(context, new WebhookExecutables(connector, context)))
        .ifPresent(existingExecutables -> existingExecutables.markAsDownAndAdd(connector));
  }

  public void deregister(RegisteredExecutable.Activated connector) {
    var context = getContext(connector);
    var executables = executablesByContext.get(context);
    if (executables == null) {
      var logMessage = "Context: " + context + " is not registered. Cannot deregister.";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }
    var requesterDeduplicationId = connector.context().getDefinition().deduplicationId();
    var registeredDeduplicationId =
        executables.getActiveWebhook().context().getDefinition().deduplicationId();

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
    var nextConnector = executables.activateNext();
    if (nextConnector.isEmpty()) {
      executablesByContext.remove(context);
    }
  }

  public void reset() {
    executablesByContext.clear();
  }

  private String getContext(RegisteredExecutable.Activated connector) {
    var properties = connector.context().bindProperties(CommonWebhookProperties.class);
    var context = properties.getContext();
    if (context == null) {
      var logMessage = "Webhook path not provided";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }
    return context;
  }
}
