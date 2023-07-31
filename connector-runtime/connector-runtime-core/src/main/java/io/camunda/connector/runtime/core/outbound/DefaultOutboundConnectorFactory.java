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
package io.camunda.connector.runtime.core.outbound;

import static io.camunda.connector.runtime.core.ConnectorHelper.instantiateConnector;

import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.discovery.EnvVarsConnectorDiscovery;
import io.camunda.connector.runtime.core.discovery.SPIConnectorDiscovery;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultOutboundConnectorFactory implements OutboundConnectorFactory {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultOutboundConnectorFactory.class);

  // stores all pre-initialized outbound connectors
  private Map<OutboundConnectorConfiguration, OutboundConnectorFunction> functionMap;

  public DefaultOutboundConnectorFactory() {
    loadConnectorConfigurations();
    if (functionMap.size() > 0) {
      LOG.debug("Registered outbound connectors: " + functionMap.keySet());
    } else {
      LOG.warn("No outbound connectors discovered");
    }
  }

  @Override
  public List<OutboundConnectorConfiguration> getConfigurations() {
    return new ArrayList<>(functionMap.keySet());
  }

  @Override
  public OutboundConnectorFunction getInstance(String type) {
    var configuration =
        functionMap.keySet().stream()
            .filter(config -> config.type().equals(type))
            .findFirst()
            .orElseThrow(
                () -> new NoSuchElementException("Connector " + type + " is not registered"));
    return functionMap.get(configuration);
  }

  @Override
  public void registerConfiguration(OutboundConnectorConfiguration configuration) {
    Optional<OutboundConnectorConfiguration> oldConfig =
        functionMap.keySet().stream()
            .filter(config -> config.type().equals(configuration.type()))
            .findAny();

    if (oldConfig.isPresent()) {
      LOG.info("Connector " + oldConfig + " is overridden, new configuration" + configuration);
      functionMap.remove(oldConfig.get());
    }
    functionMap.put(configuration, instantiateConnector(configuration.connectorClass()));
  }

  @Override
  public void resetConfigurations() {
    loadConnectorConfigurations();
  }

  protected void loadConnectorConfigurations() {
    List<OutboundConnectorConfiguration> configurations;

    if (EnvVarsConnectorDiscovery.isOutboundConfigured()) {
      configurations = EnvVarsConnectorDiscovery.discoverOutbound();
    } else {
      configurations = SPIConnectorDiscovery.discoverOutbound();
    }

    functionMap =
        configurations.stream()
            .collect(
                Collectors.toMap(
                    config -> config, config -> instantiateConnector(config.connectorClass())));
  }
}
