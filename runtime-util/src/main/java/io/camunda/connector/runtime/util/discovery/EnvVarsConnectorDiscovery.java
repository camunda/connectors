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
package io.camunda.connector.runtime.util.discovery;

import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.impl.ConnectorUtil;
import io.camunda.connector.impl.inbound.InboundConnectorConfiguration;
import io.camunda.connector.impl.outbound.OutboundConnectorConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
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

  private static Map<String, String> hardwiredEnvironmentVariables;

  public static void addHardwiredEnvironmentVariable(String key, String value) {
    if (hardwiredEnvironmentVariables == null) {
      hardwiredEnvironmentVariables = new ConcurrentHashMap<>();
    }
    hardwiredEnvironmentVariables.put(key, value);
  }

  public static void clearHardwiredEnvironmentVariable() {
    hardwiredEnvironmentVariables = null;
  }

  public static Map<String, String> getEnvironmentVariables() {
    if (hardwiredEnvironmentVariables != null) {
      HashMap<String, String> result = new HashMap<>();
      result.putAll(System.getenv());
      result.putAll(hardwiredEnvironmentVariables);
      return result;
    }
    return System.getenv();
  }

  public static boolean isInboundConfigured() {
    return getEnvironmentVariables().entrySet().stream()
        .anyMatch(entry -> INBOUND_CONNECTOR_EXECUTABLE_PATTERN.matcher(entry.getKey()).matches());
  }

  public static boolean isOutboundConfigured() {
    return getEnvironmentVariables().entrySet().stream()
        .anyMatch(entry -> OUTBOUND_CONNECTOR_FUNCTION_PATTERN.matcher(entry.getKey()).matches());
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
    return getEnvironmentVariables().keySet().stream()
        .map(pattern::matcher)
        .filter(Matcher::matches)
        .map(match -> match.group(1));
  }

  @SuppressWarnings("unchecked")
  private static OutboundConnectorConfiguration loadOutboundConfiguration(String name) {

    var functionFqdn =
        getEnv(name, "FUNCTION")
            .orElseThrow(() -> envMissing("No function specified", name, "FUNCTION"));

    try {
      var cls = (Class<? extends OutboundConnectorFunction>) Class.forName(functionFqdn);
      var annotationConfig = ConnectorUtil.getOutboundConnectorConfiguration(cls);

      return new OutboundConnectorConfiguration(
          name,
          getEnv(name, "INPUT_VARIABLES")
              .map(variables -> variables.split(","))
              .or(() -> annotationConfig.map(OutboundConnectorConfiguration::getInputVariables))
              .orElseThrow(() -> envMissing("Variables not specified", name, "INPUT_VARIABLES")),
          getEnv(name, "TYPE")
              .or(() -> annotationConfig.map(OutboundConnectorConfiguration::getType))
              .orElseThrow(() -> envMissing("Type not specified", name, "TYPE")),
          cls);

    } catch (ClassNotFoundException | ClassCastException e) {
      throw loadFailed("Failed to load " + functionFqdn, e);
    }
  }

  @SuppressWarnings("unchecked")
  private static InboundConnectorConfiguration loadInboundConfiguration(String name) {

    var executableFqdn =
        getEnv(name, "EXECUTABLE")
            .orElseThrow(() -> envMissing("No executable specified", name, "EXECUTABLE"));

    try {
      var cls = (Class<? extends InboundConnectorExecutable>) Class.forName(executableFqdn);
      var annotationConfig = ConnectorUtil.getInboundConnectorConfiguration(cls);

      return new InboundConnectorConfiguration(
          name,
          getEnv(name, "TYPE")
              .or(() -> annotationConfig.map(InboundConnectorConfiguration::getType))
              .orElseThrow(() -> envMissing("Type not specified", name, "TYPE")),
          cls);

    } catch (ClassNotFoundException | ClassCastException e) {
      throw loadFailed("Failed to load " + executableFqdn, e);
    }
  }

  private static Optional<String> getEnv(final String name, final String detail) {
    return Optional.ofNullable(getEnvironmentVariables().get("CONNECTOR_" + name + "_" + detail));
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
