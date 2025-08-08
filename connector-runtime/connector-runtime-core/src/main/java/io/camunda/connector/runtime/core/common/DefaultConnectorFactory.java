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
package io.camunda.connector.runtime.core.common;

import io.camunda.connector.runtime.core.ConnectorFactory;
import io.camunda.connector.runtime.core.config.ConnectorConfiguration;
import io.camunda.connector.runtime.core.discovery.DisabledConnectorEnvVarsConfig;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Base class for default connector factories providing common functionality for managing connector
 * configurations and creating connector instances.
 *
 * @param <T> Connector supertype
 * @param <C> Connector configuration type
 */
public abstract class DefaultConnectorFactory<T, C extends ConnectorConfiguration>
    implements ConnectorFactory<T, C> {

  private final DisabledConnectorEnvVarsConfig disabledConnectorEnvVarsConfig =
      new DisabledConnectorEnvVarsConfig();

  public record ConnectorRuntimeConfiguration<T extends ConnectorConfiguration>(
      T config, boolean isActive) {
    public Optional<T> getActiveConfig() {
      return isActive ? Optional.of(config) : Optional.empty();
    }
  }

  private Map<String, ConnectorRuntimeConfiguration<C>> configurations = new HashMap<>();

  @Override
  public Collection<C> getConfigurations() {
    return configurations.values().stream()
        .flatMap(e -> e.getActiveConfig().stream())
        .collect(Collectors.toList());
  }

  protected Optional<C> getActiveConfiguration(String type) {
    return Optional.ofNullable(configurations.get(type))
        .flatMap(ConnectorRuntimeConfiguration::getActiveConfig);
  }

  /**
   * Register a new connector configuration
   *
   * @param configuration The configuration to register
   * @throws RuntimeException if a connector with the same type is already registered
   */
  protected void registerConfiguration(C configuration) {
    var oldConfig = Optional.ofNullable(configurations.get(configuration.type()));
    oldConfig.ifPresent(c -> throwDuplicateException(c.config(), configuration));
    configurations.put(
        configuration.type(),
        new ConnectorRuntimeConfiguration<>(
            configuration, !disabledConnectorEnvVarsConfig.isConnectorDisabled(configuration)));
  }

  /**
   * Create configurations map from a list of configurations, ensuring no duplicates
   *
   * @param allConfigurations List of configurations to process
   */
  protected void initializeConfigurations(List<C> allConfigurations) {
    configurations =
        allConfigurations.stream()
            .map(
                config ->
                    new ConnectorRuntimeConfiguration<>(
                        config, !disabledConnectorEnvVarsConfig.isConnectorDisabled(config)))
            .collect(
                Collectors.toMap(
                    e -> e.config().type(),
                    config -> config,
                    (existing, replacement) ->
                        throwDuplicateException(existing.config(), replacement.config()),
                    HashMap::new));
  }

  private ConnectorRuntimeConfiguration<C> throwDuplicateException(C existing, C replacement) {
    throw new RuntimeException(
        MessageFormat.format(
            "Duplicate {0} connector registration for type: {1}. Got {2} and {3}",
            existing.direction().name().toLowerCase(), existing.type(), existing, replacement));
  }
}
