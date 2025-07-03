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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.config.ConnectorConfigurationOverrides;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.discovery.DisabledConnectorEnvVarsConfig;
import io.camunda.connector.runtime.core.discovery.EnvVarsConnectorDiscovery;
import io.camunda.connector.runtime.core.discovery.SPIConnectorDiscovery;
import io.camunda.connector.runtime.core.outbound.operation.ConnectorOperations;
import io.camunda.connector.runtime.core.outbound.operation.OutboundConnectorOperationFunction;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultOutboundConnectorFactory implements OutboundConnectorFactory {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultOutboundConnectorFactory.class);

  private final Map<String, OutboundConnectorConfiguration> configurations;
  private final Map<OutboundConnectorConfiguration, OutboundConnectorFunction>
      connectorInstanceCache = new ConcurrentHashMap<>();

  private final DisabledConnectorEnvVarsConfig disbabledConnectorEnvVarsConfig =
      new DisabledConnectorEnvVarsConfig();

  private final Function<String, String> propertyProvider;

  public DefaultOutboundConnectorFactory(
      ObjectMapper objectMapper,
      ValidationProvider validationProvider,
      List<OutboundConnectorFunction> constructorFunctions,
      List<OutboundConnectorProvider> constructorProviders,
      Function<String, String> propertyProvider) {
    this.propertyProvider = propertyProvider;

    List<OutboundConnectorConfiguration> envVarConfigurations = new ArrayList<>();
    Stream<ServiceLoader.Provider<OutboundConnectorFunction>> spiFunctions = Stream.empty();
    Stream<ServiceLoader.Provider<OutboundConnectorProvider>> spiProviders = Stream.empty();
    if (EnvVarsConnectorDiscovery.isOutboundConfigured()) {
      envVarConfigurations.addAll(EnvVarsConnectorDiscovery.discoverOutbound());
    } else {
      // Load outbound connector functions from SPI
      spiFunctions = SPIConnectorDiscovery.loadConnectorFunctions();
      spiProviders = SPIConnectorDiscovery.loadConnectorProviders();
    }

    Function<OutboundConnectorProvider, OutboundConnectorFunction> fromProviderToFunction =
        p -> {
          ConnectorOperations connectorOperations =
              ConnectorOperations.from(p, objectMapper, validationProvider);
          return new OutboundConnectorOperationFunction(connectorOperations);
        };

    // Combine all configurations from different sources from least to most important
    List<OutboundConnectorConfiguration> allConfigs = new ArrayList<>();

    // 1. SPI discovered functions
    allConfigs.addAll(
        toConfigurations(spiFunctions, ServiceLoader.Provider::type, ServiceLoader.Provider::get));

    // 2. SPI discovered providers
    allConfigs.addAll(
        toConfigurations(
            spiProviders,
            ServiceLoader.Provider::type,
            spiProviderOfConnectorProvider ->
                fromProviderToFunction.apply(spiProviderOfConnectorProvider.get())));

    // 3. Constructor supplied functions
    allConfigs.addAll(
        toConfigurations(constructorFunctions.stream(), outboundFunction -> outboundFunction));

    // 4. Constructor supplied providers
    allConfigs.addAll(toConfigurations(constructorProviders.stream(), fromProviderToFunction));

    // 5. Env vars discovered configurations
    allConfigs.addAll(envVarConfigurations);

    // Filter out disabled connectors
    Stream<OutboundConnectorConfiguration> filteredConfigs =
        allConfigs.stream()
            .filter(config -> !disbabledConnectorEnvVarsConfig.isConnectorDisabled(config));
    // Store configurations in map, with type as key (later entries override earlier ones)
    this.configurations =
        filteredConfigs.collect(
            Collectors.toMap(
                OutboundConnectorConfiguration::type,
                config -> config,
                (existing, replacement) -> {
                  LOG.warn("Overriding connector configuration {} with {}", existing, replacement);
                  return replacement;
                },
                HashMap::new));
  }

  private <T> List<OutboundConnectorConfiguration> toConfigurations(
      Stream<T> elements, Function<T, OutboundConnectorFunction> instanceProvider) {
    return toConfigurations(elements, T::getClass, instanceProvider);
  }

  private <T> List<OutboundConnectorConfiguration> toConfigurations(
      Stream<T> elements,
      Function<T, Class<?>> typeProvider,
      Function<T, OutboundConnectorFunction> instanceProvider) {
    return elements
        .filter(e -> typeProvider.apply(e).isAnnotationPresent(OutboundConnector.class))
        .map(
            e -> {
              final OutboundConnector outboundConnector =
                  typeProvider.apply(e).getAnnotation(OutboundConnector.class);
              return toConfiguration(outboundConnector, () -> instanceProvider.apply(e));
            })
        .collect(Collectors.toList());
  }

  private OutboundConnectorConfiguration toConfiguration(
      OutboundConnector outboundConnector, Supplier<OutboundConnectorFunction> instanceProvider) {
    final var configurationOverrides =
        new ConnectorConfigurationOverrides(outboundConnector.name(), this.propertyProvider);

    return new OutboundConnectorConfiguration(
        outboundConnector.name(),
        outboundConnector.inputVariables(),
        configurationOverrides.typeOverride().orElse(outboundConnector.type()),
        instanceProvider,
        configurationOverrides.timeoutOverride().orElse(null));
  }

  @Override
  public Collection<OutboundConnectorConfiguration> getConfigurations() {
    return configurations.values();
  }

  @Override
  public OutboundConnectorFunction getInstance(String type) {

    return this.getConfiguration(type)
        .map(this::getCachedInstance)
        .orElseThrow(
            () -> new RuntimeException("Outbound connector \"" + type + "\" is not registered"));
  }

  private OutboundConnectorFunction getCachedInstance(
      OutboundConnectorConfiguration configuration) {
    return connectorInstanceCache.computeIfAbsent(
        configuration, config -> config.instanceSupplier().get());
  }

  /**
   * Get configuration by type
   *
   * @param type Connector type
   * @return Optional configuration
   */
  public Optional<OutboundConnectorConfiguration> getConfiguration(String type) {
    return Optional.ofNullable(configurations.get(type));
  }
}
