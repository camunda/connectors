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
import io.camunda.connector.runtime.core.config.ConnectorConfigurationOverrides;
import io.camunda.connector.runtime.core.config.InboundConnectorConfiguration;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import java.util.Arrays;
import java.util.Optional;

public final class ConnectorUtil {

  private ConnectorUtil() {}

  public static Optional<OutboundConnectorConfiguration> getOutboundConnectorConfiguration(
      Class<? extends OutboundConnectorFunction> cls) {
    return Optional.ofNullable(cls.getAnnotation(OutboundConnector.class))
        .map(
            annotation -> {
              final var configurationOverrides =
                  new ConnectorConfigurationOverrides(annotation.name(), System::getenv);

              return new OutboundConnectorConfiguration(
                  annotation.name(),
                  annotation.inputVariables(),
                  configurationOverrides.typeOverride().orElse(annotation.type()),
                  cls,
                  configurationOverrides.timeoutOverride().orElse(null));
            });
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
}
