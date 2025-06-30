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
    var annotation = Optional.ofNullable(cls.getAnnotation(OutboundConnector.class));
    if (annotation.isPresent()) {
      final var configurationOverrides =
          new ConnectorConfigurationOverrides(annotation.get().name(), System::getenv);
      final var type =
          Optional.ofNullable(configurationOverrides.typeOverride())
              .orElse(annotation.get().type());
      final var timeout = configurationOverrides.timeoutOverride();

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
    var annotation = Optional.ofNullable(cls.getAnnotation(InboundConnector.class));
    if (annotation.isPresent()) {
      final var configurationOverrides =
          new ConnectorConfigurationOverrides(annotation.get().name(), System::getenv);
      final var type =
          Optional.ofNullable(configurationOverrides.typeOverride())
              .orElse(annotation.get().type());

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
}
