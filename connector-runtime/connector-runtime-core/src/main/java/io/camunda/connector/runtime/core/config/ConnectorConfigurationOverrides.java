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
package io.camunda.connector.runtime.core.config;

import java.util.Optional;
import java.util.function.Function;

/**
 * Handles connector overrides such as type and timeout using environment variables.
 *
 * <p>Lookup is done by normalizing the connector name to a set of variables to look for. For
 * example, for a connector named "My Connector", the following environment variables will be
 * checked:
 *
 * <ul>
 *   <li>CONNECTOR_MY_CONNECTOR_TYPE
 *   <li>CONNECTOR_MY_CONNECTOR_TIMEOUT
 * </ul>
 */
public class ConnectorConfigurationOverrides {
  private static final String TYPE_PROPERTY_TPL = "CONNECTOR_%s_TYPE";
  private static final String TIMEOUT_PROPERTY_TPL = "CONNECTOR_%s_TIMEOUT";

  private final String normalizedConnectorName;
  private final Function<String, String> propertySource;

  public ConnectorConfigurationOverrides(
      String connectorName, Function<String, String> propertySource) {
    this.normalizedConnectorName = toNormalizedConnectorName(connectorName);
    this.propertySource = propertySource;
  }

  public Optional<String> typeOverride() {
    return Optional.ofNullable(getProperty(TYPE_PROPERTY_TPL.formatted(normalizedConnectorName)));
  }

  public Optional<Long> timeoutOverride() {
    return Optional.ofNullable(getProperty(TIMEOUT_PROPERTY_TPL.formatted(normalizedConnectorName)))
        .map(Long::parseLong);
  }

  private String getProperty(String propertyName) {
    return propertySource.apply(propertyName);
  }

  /**
   * Replaces everything except alphanumeric characters, underscores, and spaces with an empty
   * string, replaces spaces with underscores, and converts to uppercase.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"My connector" -> "MY_CONNECTOR"
   *   <li>"My super-fancy connector" -> "MY_SUPERFANCY_CONNECTOR"
   *   <li>"my_connector" -> "MY_CONNECTOR"
   *   <li>"my-connector" -> "MYCONNECTOR"
   *   <li>"my.connector" -> "MYCONNECTOR"
   * </ul>
   */
  private String toNormalizedConnectorName(final String connectorName) {
    return connectorName.trim().replaceAll("[^a-zA-Z0-9_ ]", "").replaceAll(" ", "_").toUpperCase();
  }
}
