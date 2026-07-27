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
  private final boolean appendPhysicalTenantAndTenantToPath;

  public WebhookConnectorRegistry() {
    this(false);
  }

  public WebhookConnectorRegistry(boolean appendPhysicalTenantAndTenantToPath) {
    this.appendPhysicalTenantAndTenantToPath = appendPhysicalTenantAndTenantToPath;
  }

  public Optional<RegisteredExecutable.Activated> getActiveWebhook(String key) {
    return Optional.ofNullable(executablesByContext.get(key))
        .map(WebhookExecutables::getActiveWebhook);
  }

  /**
   * Physical-tenant/tenant-scoped lookup, used when {@code appendPhysicalTenantAndTenantToPath} is
   * enabled.
   */
  public Optional<RegisteredExecutable.Activated> getActiveWebhook(
      String physicalTenantId, String tenantId, String path) {
    return getActiveWebhook(WebhookContextKeys.compose(physicalTenantId, tenantId, path));
  }

  public Map<String, WebhookExecutables> getExecutablesByContext() {
    return executablesByContext;
  }

  public boolean appendsPhysicalTenantAndTenantToPath() {
    return appendPhysicalTenantAndTenantToPath;
  }

  public boolean register(RegisteredExecutable.Activated connector) {
    var rawContext = getRawContext(connector);

    WebhookConnectorValidationUtil.logIfWebhookPathDeprecated(connector, rawContext);
    var key = registryKey(connector, rawContext);
    createExecutablesOrGetExisting(key, rawContext, connector)
        .ifPresent(existingExecutables -> existingExecutables.markAsDownAndAdd(connector));

    return registeredAsActiveConnector(connector, key);
  }

  private boolean registeredAsActiveConnector(
      RegisteredExecutable.Activated connector, String key) {
    return getActiveWebhook(key).map(c -> c.equals(connector)).orElse(false);
  }

  /**
   * Creates a new {@link WebhookExecutables} instance for the given key if it does not already
   * exist (and returns an empty Optional), or returns the existing one.
   */
  private Optional<WebhookExecutables> createExecutablesOrGetExisting(
      String key, String rawContext, RegisteredExecutable.Activated connector) {
    return Optional.ofNullable(
        executablesByContext.putIfAbsent(key, new WebhookExecutables(connector, rawContext)));
  }

  public void deregister(RegisteredExecutable.Activated connector) {
    var rawContext = getRawContext(connector);
    var key = registryKey(connector, rawContext);
    var executables = executablesByContext.get(key);
    if (executables == null) {
      var logMessage = "Context: " + key + " is not registered. Cannot deregister.";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }

    var hasActiveConnector = executables.deregister(connector);
    if (!hasActiveConnector) {
      executablesByContext.remove(key);
    }
  }

  public void reset() {
    executablesByContext.clear();
  }

  private String getRawContext(RegisteredExecutable.Activated connector) {
    var properties = connector.context().bindProperties(CommonWebhookProperties.class);
    var context = properties.getContext();
    if (context == null) {
      var logMessage = "Webhook path not provided";
      LOG.debug(logMessage);
      throw new RuntimeException(logMessage);
    }
    return context;
  }

  /**
   * Computes the registry lookup key for a connector. When {@code
   * appendPhysicalTenantAndTenantToPath} is disabled (the default), this is simply the raw webhook
   * path, preserving today's behavior and URLs. When enabled, the key is scoped by both the
   * physical tenant ({@code physicalTenantId}) and the logical {@code tenantId}, so the same raw
   * path deployed on different physical tenants and/or tenants no longer collides into a single
   * {@link WebhookExecutables} instance.
   */
  private String registryKey(RegisteredExecutable.Activated connector, String rawContext) {
    if (!appendPhysicalTenantAndTenantToPath) {
      return rawContext;
    }
    var definition = connector.context().getDefinition();
    return WebhookContextKeys.compose(
        definition.physicalTenantId(), definition.tenantId(), rawContext);
  }
}
