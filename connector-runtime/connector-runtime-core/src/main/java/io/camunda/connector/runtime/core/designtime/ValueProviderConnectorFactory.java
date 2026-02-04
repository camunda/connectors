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
package io.camunda.connector.runtime.core.designtime;

import io.camunda.connector.api.designtime.ValueProvider;
import io.camunda.connector.runtime.core.ConnectorFactory;
import io.camunda.connector.runtime.core.config.ConnectorConfiguration;
import io.camunda.connector.runtime.core.discovery.SPIConnectorDiscovery;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ValueProviderConnectorFactory
    implements ConnectorFactory<ValueProvider, ConnectorConfiguration> {

  private final Map<String, ValueProvider> registryByTypeValueProviderMap;
  private final Map<RegistryKey, ValueProvider> registryKeyValueProviderMap;

  public ValueProviderConnectorFactory() {
    registryKeyValueProviderMap =
        SPIConnectorDiscovery.loadValueProviders()
            .map(ServiceLoader.Provider::get)
            .collect(
                Collectors.toMap(
                    (it) -> new RegistryKey(it.getType(), it.getName()), Function.identity()));
    registryByTypeValueProviderMap =
        SPIConnectorDiscovery.loadValueProviders()
            .map(ServiceLoader.Provider::get)
            .collect(Collectors.toMap(ValueProvider::getType, Function.identity()));
  }

  @Override
  public Collection<ConnectorConfiguration> getConfigurations() {
    return List.of();
  }

  @Override
  public ValueProvider getInstance(String type) {
    return Optional.ofNullable(registryByTypeValueProviderMap.get(type))
        .orElseThrow(() -> new NoSuchElementException("No ValueProvider found for type: " + type));
  }

  public Optional<ValueProvider> findBy(String type, String name) {
    return Optional.ofNullable(registryKeyValueProviderMap.get(new RegistryKey(type, name)));
  }

  private record RegistryKey(String type, String name) {}
  ;
}
