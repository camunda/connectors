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

import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorConfigurationRegistry {

  private static final Logger LOG =
      LoggerFactory.getLogger(OutboundConnectorConfigurationRegistry.class);

  private final Map<String, OutboundConnectorConfiguration> configurations;

  public OutboundConnectorConfigurationRegistry(
      List<OutboundConnectorConfiguration> initialConfigurations) {
    configurations =
        initialConfigurations.stream()
            .collect(
                Collectors.toConcurrentMap(OutboundConnectorConfiguration::type, config -> config));
  }

  /**
   * Get all registered configurations
   *
   * @return List of all configurations
   */
  public List<OutboundConnectorConfiguration> getConfigurations() {
    return new ArrayList<>(configurations.values());
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

  /**
   * Register a new configuration. If a connector with the same type already exists, it will be
   * overridden by the new configuration.
   *
   * @param configuration Configuration to register
   */
  public void registerConfiguration(OutboundConnectorConfiguration configuration) {
    var oldConfig = configurations.get(configuration.type());
    if (oldConfig != null) {
      LOG.info("Connector " + oldConfig + " is overridden, new configuration " + configuration);
    }
    configurations.put(configuration.type(), configuration);
  }
}
