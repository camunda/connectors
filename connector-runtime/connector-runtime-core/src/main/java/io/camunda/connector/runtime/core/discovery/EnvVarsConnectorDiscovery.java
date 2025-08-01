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

import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.ConnectorUtil;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Static functionality for discovery via environment variables */
public class EnvVarsConnectorDiscovery {

  /** Pattern describing the outbound connector env configuration pattern. */
  public static final Pattern OUTBOUND_CONNECTOR_FUNCTION_PATTERN =
      Pattern.compile("^CONNECTOR_(.*)_FUNCTION$");

  public static final Pattern INBOUND_CONNECTOR_EXECUTABLE_PATTERN =
      Pattern.compile("^CONNECTOR_(.*)_EXECUTABLE$");

  public static boolean isInboundConfigured() {
    return !discoverInbound().isEmpty();
  }

  public static boolean isOutboundConfigured() {
    return !discoverOutbound().isEmpty();
  }

  public static List<OutboundConnectorConfiguration> discoverOutbound() {
    return matchEnvVars(OUTBOUND_CONNECTOR_FUNCTION_PATTERN)
        .map(EnvVarsConnectorDiscovery::loadOutboundConfiguration)
        .collect(Collectors.toList());
  }

  public static List<InboundConnectorConfiguration> discoverInbound() {
    return matchEnvVars(INBOUND_CONNECTOR_EXECUTABLE_PATTERN)
        .map(EnvVarsConnectorDiscovery::loadInboundConfiguration)
        .collect(Collectors.toList());
  }

  private static Stream<String> matchEnvVars(Pattern pattern) {
    // match env vars against the provided pattern and return list of matching values
    return System.getenv().keySet().stream()
        .map(pattern::matcher)
        .filter(Matcher::matches)
        .map(match -> match.group(1));
  }

  @SuppressWarnings("unchecked")
  private static OutboundConnectorConfiguration loadOutboundConfiguration(String name) {

    var functionFqdn =
        getConnectorEnvironmentVariable(name, "FUNCTION")
            .orElseThrow(() -> envMissing("No function specified", name, "FUNCTION"));

    try {
      var cls = (Class<? extends OutboundConnectorFunction>) Class.forName(functionFqdn);
      Optional<OutboundConnectorConfiguration> tmpAnnotationConfig = Optional.empty();
      try {
        tmpAnnotationConfig = Optional.of(ConnectorUtil.getOutboundConnectorConfiguration(cls));
      } catch (RuntimeException e) {
        // For backward compatibility, we allow unannotated classes when constructing
        // from environment variables.
      }
      var annotationConfig = tmpAnnotationConfig;
      return new OutboundConnectorConfiguration(
          name,
          getConnectorEnvironmentVariable(name, "INPUT_VARIABLES")
              .map(variables -> variables.split(","))
              .or(() -> annotationConfig.map(OutboundConnectorConfiguration::inputVariables))
              .orElseThrow(() -> envMissing("Variables not specified", name, "INPUT_VARIABLES")),
          getConnectorEnvironmentVariable(name, "TYPE")
              .or(() -> annotationConfig.map(OutboundConnectorConfiguration::type))
              .orElseThrow(() -> envMissing("Type not specified", name, "TYPE")),
          () -> ConnectorHelper.instantiateConnector(cls),
          getConnectorEnvironmentVariable(name, "TIMEOUT")
              .map(Long::parseLong)
              .or(() -> annotationConfig.map(OutboundConnectorConfiguration::timeout))
              .orElse(null));

    } catch (ClassNotFoundException | ClassCastException e) {
      throw loadFailed("Failed to load " + functionFqdn, e);
    }
  }

  @SuppressWarnings("unchecked")
  private static InboundConnectorConfiguration loadInboundConfiguration(String name) {

    var executableFqdn =
        getConnectorEnvironmentVariable(name, "EXECUTABLE")
            .orElseThrow(() -> envMissing("No executable specified", name, "EXECUTABLE"));

    try {
      var cls = (Class<? extends InboundConnectorExecutable>) Class.forName(executableFqdn);
      Optional<InboundConnectorConfiguration> tmpAnnotationConfig = Optional.empty();
      try {
        tmpAnnotationConfig = Optional.of(ConnectorUtil.getInboundConnectorConfiguration(cls));
      } catch (RuntimeException e) {
        // For backward compatibility, we allow unannotated classes when constructing
        // from environment variables.
      }
      var annotationConfig = tmpAnnotationConfig;

      return new InboundConnectorConfiguration(
          name,
          getConnectorEnvironmentVariable(name, "TYPE")
              .or(() -> annotationConfig.map(InboundConnectorConfiguration::type))
              .orElseThrow(() -> envMissing("Type not specified", name, "TYPE")),
          cls,
          getConnectorEnvironmentVariable(name, "DEDUPLICATION_PROPERTIES")
              .map(properties -> properties.split(","))
              .map(Arrays::asList)
              .or(
                  () ->
                      annotationConfig.map(InboundConnectorConfiguration::deduplicationProperties))
              .orElseThrow(
                  () ->
                      envMissing(
                          "Deduplication properties not specified",
                          name,
                          "DEDUPLICATION_PROPERTIES")));

    } catch (ClassNotFoundException | ClassCastException e) {
      throw loadFailed("Failed to load " + executableFqdn, e);
    }
  }

  private static RuntimeException loadFailed(String s, Exception e) {
    return new IllegalStateException(s, e);
  }

  private static RuntimeException envMissing(String message, String name, String envKey) {
    return new RuntimeException(
        String.format(
            "%s: Please configure it via CONNECTOR_%s_%s environment variable",
            message, name, envKey));
  }
}
