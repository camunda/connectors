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

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.config.ConnectorConfigurationOverrides;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultOutboundConnectorFactory implements OutboundConnectorFactory {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DefaultOutboundConnectorFactory.class);

  private final Map<String, OutboundConnectorConfiguration> connectorConfigs;

  private final Map<OutboundConnectorConfiguration, OutboundConnectorFunction>
      connectorInstanceCache;

  public DefaultOutboundConnectorFactory(
      List<OutboundConnectorFunction> functions, Function<String, String> propertySource) {
    // Once we are on Java 22+ we can assign this to a temporary variable before the constructor
    // call
    this(
        functions.stream()
            .filter(f -> f.getClass().isAnnotationPresent(OutboundConnector.class))
            .map(
                f -> {
                  final OutboundConnector outboundConnector =
                      f.getClass().getAnnotation(OutboundConnector.class);
                  return createConnectorConfiguration(outboundConnector, f, propertySource);
                })
            .toList());
  }

  /**
   * @param configurations List of {@link OutboundConnectorConfiguration} that will be used to
   *     create {@link OutboundConnectorFunction} instances. As there can only be one instance per
   *     {@link OutboundConnectorConfiguration#type()}, later entries with the same type will
   *     override earlier ones.
   */
  public DefaultOutboundConnectorFactory(List<OutboundConnectorConfiguration> configurations) {
    List<OutboundConnectorConfiguration> configs =
        new ArrayList<>(OutboundConnectorDiscovery.loadConnectorConfigurations());
    configs.addAll(configurations);
    connectorConfigs =
        configs.stream()
            .collect(
                Collectors.toConcurrentMap(
                    OutboundConnectorConfiguration::type,
                    config -> config,
                    (present, newEntry) -> newEntry));
    connectorInstanceCache = new ConcurrentHashMap<>();
  }

  private static OutboundConnectorConfiguration createConnectorConfiguration(
      OutboundConnector outboundConnector,
      OutboundConnectorFunction bean,
      Function<String, String> propertySource) {
    final var configurationOverrides =
        new ConnectorConfigurationOverrides(outboundConnector.name(), propertySource);

    OutboundConnectorConfiguration configuration =
        new OutboundConnectorConfiguration(
            outboundConnector.name(),
            outboundConnector.inputVariables(),
            configurationOverrides.typeOverride().orElse(outboundConnector.type()),
            bean.getClass(),
            () -> bean,
            configurationOverrides.timeoutOverride().orElse(null));
    LOGGER.info("Configuring outbound connector {}", configuration);
    return configuration;
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
}
