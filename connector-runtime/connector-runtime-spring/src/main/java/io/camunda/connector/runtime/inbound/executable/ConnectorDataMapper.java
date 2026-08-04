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
package io.camunda.connector.runtime.inbound.executable;

import io.camunda.connector.runtime.core.inbound.InboundConnectorElement;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import io.camunda.connector.runtime.inbound.webhook.WebhookContextKeys;
import java.util.HashMap;
import java.util.Map;

public class ConnectorDataMapper {

  private static final String INBOUND_CONTEXT_PROPERTY = "inbound.context";

  private final boolean appendPhysicalTenantAndTenantToPath;

  public ConnectorDataMapper() {
    this(false);
  }

  public ConnectorDataMapper(boolean appendPhysicalTenantAndTenantToPath) {
    this.appendPhysicalTenantAndTenantToPath = appendPhysicalTenantAndTenantToPath;
  }

  /**
   * Rewrites {@code inbound.context} to the physical-tenant/tenant-scoped path when path scoping is
   * enabled (both at the server level and for this specific call, see {@link
   * #createActiveInboundConnectorResponse(ActiveExecutableResponse, boolean)}). Whether this
   * connector is a webhook is decided by the presence of {@code inbound.context} itself — a
   * property exclusive to the webhook element template — rather than {@code
   * response.executableClass()}: that field is only populated once a connector has successfully
   * activated (see {@code InboundExecutableQueryService}), so a webhook that failed to activate
   * would otherwise keep showing its legacy, unscoped path even though the registry would only ever
   * accept the scoped one once it does activate.
   */
  private Map<String, String> allPropertiesMapper(
      ActiveExecutableResponse response, boolean appendPhysicalTenantAndTenantToPath) {
    var firstElement = response.elements().getFirst();
    var properties = firstElement.connectorLevelProperties();
    if (appendPhysicalTenantAndTenantToPath) {
      var rawContext = properties.get(INBOUND_CONTEXT_PROPERTY);
      if (rawContext != null) {
        properties = new HashMap<>(properties);
        properties.put(
            INBOUND_CONTEXT_PROPERTY,
            WebhookContextKeys.compose(
                firstElement.physicalTenantId(), firstElement.tenantId(), rawContext));
      }
    }
    return properties;
  }

  /** Equivalent to {@code createActiveInboundConnectorResponse(connector, true)}. */
  public ActiveInboundConnectorResponse createActiveInboundConnectorResponse(
      ActiveExecutableResponse connector) {
    return createActiveInboundConnectorResponse(connector, true);
  }

  /**
   * @param appendPhysicalTenantAndTenantToPath whether *this call* wants the path rewritten, on top
   *     of the server-wide {@code appendPhysicalTenantAndTenantToPath} passed to the constructor —
   *     both must be true for the rewrite to actually happen. Callers that always want the
   *     effective server setting (e.g. {@code InboundConnectorRestController}) pass {@code true}
   *     here (or use the single-argument overload); callers that expose this as an opt-in, e.g. a
   *     request query parameter (e.g. {@code InboundInstancesService}, to stay backward-compatible
   *     for clients that have not yet adapted to the scoped path) pass the caller's choice through.
   */
  public ActiveInboundConnectorResponse createActiveInboundConnectorResponse(
      ActiveExecutableResponse connector, boolean appendPhysicalTenantAndTenantToPath) {
    var elements = connector.elements();
    var logs = connector.logs();
    var type = elements.getFirst().type();
    var tenantId = elements.getFirst().element().tenantId();
    var physicalTenantId = elements.getFirst().physicalTenantId();
    return new ActiveInboundConnectorResponse(
        connector.executableId(),
        type,
        tenantId,
        elements.stream().map(InboundConnectorElement::element).toList(),
        allPropertiesMapper(
            connector,
            this.appendPhysicalTenantAndTenantToPath && appendPhysicalTenantAndTenantToPath),
        connector.health(),
        connector.activationTimestamp(),
        logs,
        physicalTenantId);
  }
}
