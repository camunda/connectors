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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.ConnectorHelper;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.operation.ConnectorOperations;
import io.camunda.connector.runtime.core.outbound.operation.OutboundConnectorOperationFunction;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultOutboundConnectorFactory implements OutboundConnectorFactory {

  private static final Logger LOG = LoggerFactory.getLogger(DefaultOutboundConnectorFactory.class);

  private final ObjectMapper objectMapper;
  private final ValidationProvider validationProvider;
  private final OutboundConnectorConfigurationRegistry configurationRegistry;

  private final Map<OutboundConnectorConfiguration, OutboundConnectorFunction>
      connectorInstanceCache;

  public DefaultOutboundConnectorFactory(
      OutboundConnectorConfigurationRegistry configurationRegistry,
      ObjectMapper objectMapper,
      ValidationProvider validationProvider) {
    this.configurationRegistry = configurationRegistry;
    this.objectMapper = objectMapper;
    this.validationProvider = validationProvider;
    this.connectorInstanceCache = new ConcurrentHashMap<>();
  }

  @Override
  public List<OutboundConnectorConfiguration> getConfigurations() {
    return configurationRegistry.getConfigurations();
  }

  @Override
  public OutboundConnectorFunction getInstance(String type) {
    return configurationRegistry
        .getConfiguration(type)
        .map(this::createCachedInstance)
        .orElseThrow(
            () -> new RuntimeException("Outbound connector \"" + type + "\" is not registered"));
  }

  private OutboundConnectorFunction createCachedInstance(OutboundConnectorConfiguration config) {
    return connectorInstanceCache.computeIfAbsent(config, this::createFunctionInstance);
  }

  private OutboundConnectorFunction createFunctionInstance(OutboundConnectorConfiguration config) {
    var instance = createConnectorInstance(config);
    switch (instance) {
      case OutboundConnectorFunction function -> {
        return function;
      }
      case OutboundConnectorProvider provider -> {
        ConnectorOperations connectorOperations =
            ConnectorOperations.from(provider, objectMapper, validationProvider);
        return new OutboundConnectorOperationFunction(connectorOperations);
      }
      default ->
          throw new IllegalArgumentException(
              "Connector class "
                  + instance.getClass().getName()
                  + " does not implement OutboundConnectorFunction or OutboundConnectorProvider");
    }
  }

  private static Object createConnectorInstance(OutboundConnectorConfiguration config) {
    if (config.customInstanceSupplier() != null) {
      return config.customInstanceSupplier().get();
    } else {
      return ConnectorHelper.instantiateConnector(config.connectorClass());
    }
  }
}
