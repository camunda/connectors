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
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.runtime.core.config.ConnectorConfigurationOverrides;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorConfigurationRegistry {

  private static final Logger LOG =
      LoggerFactory.getLogger(OutboundConnectorConfigurationRegistry.class);

  private final Map<String, OutboundConnectorConfiguration> configurations;

  public OutboundConnectorConfigurationRegistry(
      List<OutboundConnectorConfiguration> configs,
      List<OutboundConnectorFunction> functions,
      List<OutboundConnectorProvider> providers,
      Function<String, String> propertyProvider) {

    // Combine all configurations from different sources
    Stream<OutboundConnectorConfiguration> allConfigs =
        Stream.of(
                OutboundConnectorDiscovery.loadConnectorConfigurations().stream(),
                configs.stream(),
                processFunctions(propertyProvider, functions).stream(),
                processProviders(propertyProvider, providers).stream())
            .flatMap(s -> s);

    // Store configurations in map, with type as key (later entries override earlier ones)
    this.configurations =
        allConfigs.collect(
            Collectors.toMap(
                OutboundConnectorConfiguration::type,
                config -> config,
                (existing, replacement) -> {
                  LOG.info("Overriding connector configuration {} with {}", existing, replacement);
                  return replacement;
                },
                HashMap::new));
  }

  private Collection<OutboundConnectorConfiguration> processProviders(
      Function<String, String> propertyProvider, List<OutboundConnectorProvider> providers) {
    return providers.stream()
        .filter(p -> p.getClass().isAnnotationPresent(OutboundConnector.class))
        .map(
            p -> {
              final OutboundConnector outboundConnector =
                  p.getClass().getAnnotation(OutboundConnector.class);
              return deriveConnectorConfig(outboundConnector, p, propertyProvider);
            })
        .collect(Collectors.toList());
  }

  List<OutboundConnectorConfiguration> processFunctions(
      Function<String, String> propertyProvider, List<OutboundConnectorFunction> functions) {
    return functions.stream()
        .filter(f -> f.getClass().isAnnotationPresent(OutboundConnector.class))
        .map(
            f -> {
              final OutboundConnector outboundConnector =
                  f.getClass().getAnnotation(OutboundConnector.class);
              return deriveConnectorConfig(outboundConnector, f, propertyProvider);
            })
        .collect(Collectors.toList());
  }

  private OutboundConnectorConfiguration deriveConnectorConfig(
      OutboundConnector outboundConnector,
      OutboundConnectorFunction function,
      Function<String, String> propertyProvider) {
    final var configurationOverrides =
        new ConnectorConfigurationOverrides(outboundConnector.name(), propertyProvider);

    OutboundConnectorConfiguration configuration =
        new OutboundConnectorConfiguration(
            outboundConnector.name(),
            outboundConnector.inputVariables(),
            configurationOverrides.typeOverride().orElse(outboundConnector.type()),
            function.getClass(),
            () -> function,
            configurationOverrides.timeoutOverride().orElse(null));

    return configuration;
  }

  private OutboundConnectorConfiguration deriveConnectorConfig(
      OutboundConnector outboundConnector,
      OutboundConnectorProvider provider,
      Function<String, String> propertyProvider) {
    final var configurationOverrides =
        new ConnectorConfigurationOverrides(outboundConnector.name(), propertyProvider);

    OutboundConnectorConfiguration configuration =
        new OutboundConnectorConfiguration(
            outboundConnector.name(),
            outboundConnector.inputVariables(),
            configurationOverrides.typeOverride().orElse(outboundConnector.type()),
            provider.getClass(),
            // No supplier function as instances are created via reflection
            configurationOverrides.timeoutOverride().orElse(null));

    return configuration;
  }

  public Map<String, OutboundConnectorConfiguration> getConfigurations() {
    return configurations;
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
