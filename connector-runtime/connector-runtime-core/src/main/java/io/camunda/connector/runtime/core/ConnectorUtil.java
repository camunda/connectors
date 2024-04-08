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
package io.camunda.connector.runtime.core;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConnectorUtil {

  private ConnectorUtil() {}

  public static Optional<OutboundConnectorConfiguration> getOutboundConnectorConfiguration(
      Class<? extends OutboundConnectorFunction> cls) {
    Map<String, String> env = System.getenv();
    var annotation = Optional.ofNullable(cls.getAnnotation(OutboundConnector.class));
    if (annotation.isPresent()) {
      final var normalizedConnectorName =
          toConnectorTypeEnvVariable(toNormalizedConnectorName(annotation.get().name()));
      final var normalizedConnectorTimeout =
          toConnectorTimeoutEnvVariable(toNormalizedConnectorName(annotation.get().name()));
      final var type =
          Optional.ofNullable(env.get(normalizedConnectorName)).orElse(annotation.get().type());
      final var timeout =
          Optional.ofNullable(env.get(normalizedConnectorTimeout))
              .map(Long::parseLong)
              .orElse(null);
      return Optional.of(
          new OutboundConnectorConfiguration(
              annotation.get().name(), annotation.get().inputVariables(), type, cls, timeout));
    }
    return Optional.empty();
  }

  public static OutboundConnectorConfiguration getRequiredOutboundConnectorConfiguration(
      Class<? extends OutboundConnectorFunction> cls) {
    return getOutboundConnectorConfiguration(cls)
        .orElseThrow(
            () ->
                new RuntimeException(
                    String.format(
                        "OutboundConnectorFunction %s is missing @OutboundConnector annotation",
                        cls)));
  }

  public static Optional<InboundConnectorConfiguration> getInboundConnectorConfiguration(
      Class<? extends InboundConnectorExecutable> cls) {
    Map<String, String> env = System.getenv();
    var annotation = Optional.ofNullable(cls.getAnnotation(InboundConnector.class));
    if (annotation.isPresent()) {
      final var normalizedConnectorName =
          toConnectorTypeEnvVariable(toNormalizedConnectorName(annotation.get().name()));
      final var type =
          Optional.ofNullable(env.get(normalizedConnectorName)).orElse(annotation.get().type());
      final var deduplicationProperties = Arrays.asList(annotation.get().deduplicationProperties());
      return Optional.of(
          new InboundConnectorConfiguration(
              annotation.get().name(), type, cls, deduplicationProperties));
    }
    return Optional.empty();
  }

  public static InboundConnectorConfiguration getRequiredInboundConnectorConfiguration(
      Class<? extends InboundConnectorExecutable> cls) {
    return getInboundConnectorConfiguration(cls)
        .orElseThrow(
            () ->
                new RuntimeException(
                    String.format(
                        "InboundConnectorExecutable %s is missing @InboundConnector annotation",
                        cls)));
  }

  public static String replaceTokens(
      String original, Pattern pattern, Function<Matcher, String> converter) {
    int lastIndex = 0;
    StringBuilder output = new StringBuilder();
    Matcher matcher = pattern.matcher(original);
    while (matcher.find()) {
      output.append(original, lastIndex, matcher.start()).append(converter.apply(matcher));
      lastIndex = matcher.end();
    }
    if (lastIndex < original.length()) {
      output.append(original, lastIndex, original.length());
    }
    return output.toString();
  }

  private static String toNormalizedConnectorName(final String connectorName) {
    return connectorName.trim().replaceAll("[^a-zA-Z0-9_ ]", "").replaceAll(" ", "_").toUpperCase();
  }

  private static String toConnectorTypeEnvVariable(final String normalizedConnectorName) {
    return "CONNECTOR_" + normalizedConnectorName + "_TYPE";
  }

  private static String toConnectorTimeoutEnvVariable(final String normalizedConnectorName) {
    return "CONNECTOR_" + normalizedConnectorName + "_TIMEOUT";
  }
}
