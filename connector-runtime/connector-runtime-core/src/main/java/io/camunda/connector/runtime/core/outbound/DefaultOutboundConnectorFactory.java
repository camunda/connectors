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

import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultOutboundConnectorFactory implements OutboundConnectorFactory {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultOutboundConnectorFactory.class);

  private final Map<String, OutboundConnectorConfiguration> connectorConfigs;

  private final Map<OutboundConnectorConfiguration, OutboundConnectorFunction>
      connectorInstanceCache;

  public DefaultOutboundConnectorFactory(List<OutboundConnectorConfiguration> configurations) {
    connectorConfigs =
        configurations.stream()
            .collect(
                Collectors.toConcurrentMap(OutboundConnectorConfiguration::type, config -> config));
    connectorInstanceCache = new ConcurrentHashMap<>();
  }

  @Override
  public List<OutboundConnectorConfiguration> getConfigurations() {
    return new ArrayList<>(connectorConfigs.values());
  }

  @Override
  public OutboundConnectorFunction getInstance(String type) {
    return Optional.ofNullable(connectorConfigs.get(type))
        .map(this::createInstance)
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    "Outbound connector \"" + type + "\" is not registered"));
  }

  private OutboundConnectorFunction createInstance(OutboundConnectorConfiguration config) {
    return connectorInstanceCache.computeIfAbsent(
        config,
        c -> {
          if (c.customInstanceSupplier() != null) {
            return c.customInstanceSupplier().get();
          } else {
            return ConnectorHelper.instantiateConnector(c.connectorClass());
          }
        });
  }

  @Override
  public void registerConfiguration(OutboundConnectorConfiguration configuration) {
    var oldConfig = connectorConfigs.get(configuration.type());
    if (oldConfig != null) {
      LOG.info("Connector " + oldConfig + " is overridden, new configuration" + configuration);
      connectorConfigs.remove(oldConfig.type());
    }
    connectorConfigs.put(configuration.type(), configuration);
  }

  @Override
  public void resetConfigurations() {
    connectorConfigs.clear();
    connectorInstanceCache.clear();
  }
}
