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
package io.camunda.connector.runtime.core.inbound;

import static io.camunda.connector.runtime.core.ConnectorHelper.instantiateConnector;

import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.runtime.core.common.DefaultConnectorFactory;
import io.camunda.connector.runtime.core.config.ConnectorDirection;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.discovery.DisabledConnectorEnvVarsConfig;
import io.camunda.connector.runtime.core.discovery.EnvVarsConnectorDiscovery;
import io.camunda.connector.runtime.core.discovery.SPIConnectorDiscovery;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for inbound Connectors.
 *
 * <p>Unlike outbound Connectors, which are stateless functions, inbound Connectors are stateful.
 * They have a lifecycle, can be deactivated, and a new instance of an inbound Connector is required
 * each time. Therefore, this factory actually creates a new object every time a Connector instance
 * is requested.
 */
public class DefaultInboundConnectorFactory
    extends DefaultConnectorFactory<
        InboundConnectorExecutable<InboundConnectorContext>, InboundConnectorConfiguration>
    implements InboundConnectorFactory {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultInboundConnectorFactory.class);

  public DefaultInboundConnectorFactory() {
    super();
    List<InboundConnectorConfiguration> input;
    if (DisabledConnectorEnvVarsConfig.isDiscoveryDisabled(ConnectorDirection.INBOUND)) {
      return;
    }
    if (EnvVarsConnectorDiscovery.isInboundConfigured()) {
      input = EnvVarsConnectorDiscovery.discoverInbound();
    } else {
      input = SPIConnectorDiscovery.discoverInbound();
    }
    initializeConfigurations(input);
    var config = getConfigurations();
    if (!config.isEmpty()) {
      LOG.debug("Registered inbound connectors: {}", config);
    } else {
      LOG.warn("No inbound connectors discovered");
    }
  }

  @Override
  public InboundConnectorExecutable<InboundConnectorContext> getInstance(String type) {
    var configuration =
        this.getActiveConfiguration(type)
            .orElseThrow(
                () -> new RuntimeException("Inbound connector \"" + type + "\" is not registered"));

    return createInstance(configuration);
  }

  private InboundConnectorExecutable<InboundConnectorContext> createInstance(
      InboundConnectorConfiguration configuration) {
    if (configuration.customInstanceSupplier() != null) {
      return (InboundConnectorExecutable<InboundConnectorContext>)
          configuration.customInstanceSupplier().get();
    } else {
      return (InboundConnectorExecutable<InboundConnectorContext>)
          instantiateConnector(configuration.connectorClass());
    }
  }

  @Override
  public void registerConfiguration(InboundConnectorConfiguration configuration) {
    super.registerConfiguration(configuration);
  }
}
