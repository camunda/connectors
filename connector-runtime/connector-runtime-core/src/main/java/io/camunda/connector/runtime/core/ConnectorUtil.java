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

import static io.camunda.connector.api.reflection.ReflectionUtil.getMethodsAnnotatedWith;
import static io.camunda.connector.api.reflection.ReflectionUtil.getVariableName;

import io.camunda.connector.api.annotation.InboundConnector;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.reflection.ReflectionUtil;
import io.camunda.connector.api.reflection.ReflectionUtil.MethodWithAnnotation;
import io.camunda.connector.runtime.core.config.ConnectorConfigurationOverrides;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import java.lang.reflect.Field;
import java.util.*;
import java.util.Arrays;
import java.util.Optional;

/**
 * Utility class for handling connector configurations and operations. Provides methods to retrieve
 * outbound and inbound connector configurations, as well as to extract input variables from
 * annotated methods.
 */
public final class ConnectorUtil {

  private ConnectorUtil() {}

  public static Optional<OutboundConnectorConfiguration> getOutboundConnectorConfiguration(
      Class<?> cls) {
    return Optional.ofNullable(cls.getAnnotation(OutboundConnector.class))
        .map(
            annotation -> {
              final var configurationOverrides =
                  new ConnectorConfigurationOverrides(annotation.name(), System::getenv);

              return new OutboundConnectorConfiguration(
                  annotation.name(),
                  getInputVariables(cls, annotation),
                  configurationOverrides.typeOverride().orElse(annotation.type()),
                  cls,
                  configurationOverrides.timeoutOverride().orElse(null));
            });
  }

  public static OutboundConnectorConfiguration getRequiredOutboundConnectorConfiguration(
      Class<?> cls) {
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
    return Optional.ofNullable(cls.getAnnotation(InboundConnector.class))
        .map(
            annotation -> {
              final var configurationOverrides =
                  new ConnectorConfigurationOverrides(annotation.name(), System::getenv);
              final var deduplicationProperties =
                  Arrays.asList(annotation.deduplicationProperties());

              return new InboundConnectorConfiguration(
                  annotation.name(),
                  configurationOverrides.typeOverride().orElse(annotation.type()),
                  cls,
                  deduplicationProperties);
            });
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

  private static String toNormalizedConnectorName(final String connectorName) {
    return connectorName.trim().replaceAll("[^a-zA-Z0-9_ ]", "").replaceAll(" ", "_").toUpperCase();
  }

  private static String toConnectorTypeEnvVariable(final String normalizedConnectorName) {
    return "CONNECTOR_" + normalizedConnectorName + "_TYPE";
  }

  private static String toConnectorTimeoutEnvVariable(final String normalizedConnectorName) {
    return "CONNECTOR_" + normalizedConnectorName + "_TIMEOUT";
  }

  public static String[] getInputVariables(Class<?> cls, OutboundConnector annotation) {
    List<MethodWithAnnotation<Operation>> operations =
        getMethodsAnnotatedWith(cls, Operation.class);
    if (operations.isEmpty()) {
      return annotation.inputVariables();
    } else {
      return getInputVariables(operations).toArray(new String[0]);
    }
  }

  public static Set<String> getInputVariables(
      List<ReflectionUtil.MethodWithAnnotation<Operation>> operations) {
    Set<String> variables = new HashSet<>();
    operations.forEach(
        method -> {
          method.parameters().stream()
              .filter(
                  (p) ->
                      p.getAnnotation(io.camunda.connector.api.annotation.Variable.class) != null)
              .forEach(
                  parameter -> {
                    Variable variable =
                        parameter.getAnnotation(io.camunda.connector.api.annotation.Variable.class);
                    String variableName = getVariableName(variable);
                    if (variableName.isEmpty()) {
                      // When the variable name is empty, we assume that the variables are mapped
                      // from the root
                      for (Field declaredField : parameter.getType().getDeclaredFields()) {
                        declaredField.setAccessible(true);
                        String fieldName = declaredField.getName();
                        variables.add(fieldName);
                      }
                    } else {
                      variables.add(variableName);
                    }
                  });
        });
    return variables;
  }
}
