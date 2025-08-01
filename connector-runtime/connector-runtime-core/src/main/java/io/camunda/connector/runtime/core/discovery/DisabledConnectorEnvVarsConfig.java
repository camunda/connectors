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
package io.camunda.connector.runtime.core.discovery;

import static io.camunda.connector.runtime.core.discovery.ConnectorEnvVars.getConnectorEnvironmentVariable;

import io.camunda.connector.runtime.core.config.ConnectorConfiguration;
import io.camunda.connector.runtime.core.config.ConnectorDirection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DisabledConnectorEnvVarsConfig {
  private static final Logger LOG = LoggerFactory.getLogger(DisabledConnectorEnvVarsConfig.class);

  private final HashMap<ConnectorDirection, Set<String>> envVarCache = new HashMap<>();

  public static boolean isDiscoveryDisabled(ConnectorDirection direction) {
    return getConnectorEnvironmentVariable(direction.name(), "DISCOVERY_DISABLED").isPresent();
  }

  public boolean isConnectorDisabled(ConnectorConfiguration config) {

    var isDisabled = isConnectorDisabled(config.direction(), config.type().toLowerCase());
    if (isDisabled) {
      LOG.info(
          "Connector {} has been disabled by the CONNECTOR_{}_DISABLED environment variable",
          config.type(),
          config.direction().name());
    }
    return isDisabled;
  }

  private boolean isConnectorDisabled(ConnectorDirection connectorDirection, String type) {
    return getDisabledConnectorTypes(connectorDirection).contains(type);
  }

  private Set<String> getDisabledConnectorTypes(ConnectorDirection direction) {
    return envVarCache.computeIfAbsent(
        direction,
        key ->
            getConnectorEnvironmentVariable(key.name(), "DISABLED")
                .map(
                    value ->
                        Arrays.stream(value.split(","))
                            .map(String::trim)
                            .map(String::toLowerCase)
                            .filter(e -> !e.isBlank())
                            .collect(Collectors.toSet()))
                .orElse(Set.of()));
  }
}
