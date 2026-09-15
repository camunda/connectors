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
package io.camunda.connector.runtime.inbound;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.runtime.core.inbound.InboundConnectorContextFactory;
import io.camunda.connector.runtime.core.inbound.activitylog.ActivityLogWriter;
import io.camunda.connector.runtime.core.inbound.details.InboundConnectorDetails.ValidInboundConnectorDetails;
import io.camunda.connector.runtime.inbound.search.SearchQueryClientRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Dispatches context creation to the {@link InboundConnectorContextFactory} delegate wired for the
 * physical tenant the connector's process was deployed on, so that the resulting context (and the
 * {@code CamundaClient} it carries for correlation) is always bound to the correct physical tenant.
 */
public class PhysicalTenantIdRoutingInboundConnectorContextFactory
    implements InboundConnectorContextFactory {

  private final Map<String, InboundConnectorContextFactory> delegatesByPhysicalTenantId;
  private final Optional<SearchQueryClientRegistry> searchQueryClientRegistry;

  public PhysicalTenantIdRoutingInboundConnectorContextFactory(
      Map<String, InboundConnectorContextFactory> delegatesByPhysicalTenantId) {
    this(delegatesByPhysicalTenantId, null);
  }

  /**
   * {@code searchQueryClientRegistry}, when supplied, resolves a physical tenant ID that has no
   * delegate here back to the client name {@code delegatesByPhysicalTenantId} was actually keyed by
   * at construction time. Both maps are keyed the same way at startup (the client's configured
   * name, whenever its real physical tenant ID isn't readable yet); but only the registry tracks
   * that a client's later {@code onStart} can migrate its own key to the resolved physical tenant
   * ID, so it — not this class — is what can bridge an incoming connector element already tagged
   * with the post-migration ID back to the delegate this map still has filed under the client's
   * name.
   */
  public PhysicalTenantIdRoutingInboundConnectorContextFactory(
      Map<String, InboundConnectorContextFactory> delegatesByPhysicalTenantId,
      SearchQueryClientRegistry searchQueryClientRegistry) {
    this.delegatesByPhysicalTenantId = delegatesByPhysicalTenantId;
    this.searchQueryClientRegistry = Optional.ofNullable(searchQueryClientRegistry);
  }

  @Override
  public <T extends InboundConnectorExecutable<?>> InboundConnectorContext createContext(
      final ValidInboundConnectorDetails connectorDetails,
      final Consumer<Throwable> cancellationCallback,
      final Class<T> executableClass,
      final ActivityLogWriter logWriter) {
    var physicalTenantId = connectorDetails.connectorElements().getFirst().physicalTenantId();
    var delegate = resolveDelegate(physicalTenantId);
    if (delegate == null) {
      throw new IllegalStateException(
          "No CamundaClient configured for physical tenant '" + physicalTenantId + "'");
    }
    return delegate.createContext(
        connectorDetails, cancellationCallback, executableClass, logWriter);
  }

  private InboundConnectorContextFactory resolveDelegate(String physicalTenantId) {
    var delegate = delegatesByPhysicalTenantId.get(physicalTenantId);
    if (delegate != null) {
      return delegate;
    }
    return searchQueryClientRegistry
        .flatMap(registry -> registry.clientNameFor(physicalTenantId))
        .map(delegatesByPhysicalTenantId::get)
        .orElse(null);
  }
}
