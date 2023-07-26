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

import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.discovery.EnvVarsConnectorDiscovery;
import io.camunda.connector.runtime.core.discovery.SPIConnectorDiscovery;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
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
public class DefaultInboundConnectorFactory implements InboundConnectorFactory {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultInboundConnectorFactory.class);

  private List<InboundConnectorConfiguration> configurations;

  public DefaultInboundConnectorFactory() {
    loadConnectorConfigurations();
    if (configurations.size() > 0) {
      LOG.debug("Registered inbound connectors: " + configurations);
    } else {
      LOG.warn("No inbound connectors discovered");
    }
  }

  @Override
  public List<InboundConnectorConfiguration> getConfigurations() {
    return configurations;
  }

  @Override
  public InboundConnectorExecutable getInstance(String type) {

    InboundConnectorConfiguration configuration =
        configurations.stream()
            .filter(config -> config.type().equals(type))
            .findFirst()
            .orElseThrow(
                () -> new NoSuchElementException("Connector " + type + " is not registered"));

    return instantiateConnector(configuration.connectorClass());
  }

  @Override
  public void registerConfiguration(InboundConnectorConfiguration configuration) {
    Optional<InboundConnectorConfiguration> oldConfig =
        configurations.stream()
            .filter(config -> config.type().equals(configuration.type()))
            .findAny();

    if (oldConfig.isPresent()) {
      LOG.info("Connector " + oldConfig + " is overridden, new configuration" + configuration);
      configurations.remove(oldConfig.get());
    }
    configurations.add(configuration);
  }

  @Override
  public void resetConfigurations() {
    loadConnectorConfigurations();
  }

  protected void loadConnectorConfigurations() {
    if (EnvVarsConnectorDiscovery.isInboundConfigured()) {
      configurations = EnvVarsConnectorDiscovery.discoverInbound();
    } else {
      configurations = SPIConnectorDiscovery.discoverInbound();
    }
  }
}
