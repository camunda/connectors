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
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.common.AbstractConnectorFactory;
import io.camunda.connector.runtime.core.config.ConnectorConfigurationOverrides;
import io.camunda.connector.runtime.core.config.ConnectorDirection;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.discovery.DisabledConnectorEnvVarsConfig;
import io.camunda.connector.runtime.core.discovery.EnvVarsConnectorDiscovery;
import io.camunda.connector.runtime.core.discovery.SPIConnectorDiscovery;
import io.camunda.connector.runtime.core.outbound.operation.ConnectorOperations;
import io.camunda.connector.runtime.core.outbound.operation.OutboundConnectorOperationFunction;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DefaultOutboundConnectorFactory
    extends AbstractConnectorFactory<OutboundConnectorFunction, OutboundConnectorConfiguration>
    implements OutboundConnectorFactory {

  /**
   * Pairs an {@link OutboundConnectorFunction} type (for reading its {@link OutboundConnector}
   * annotation) with a {@link Supplier} that resolves an instance of it. Unlike a plain
   * already-constructed instance, the supplier is invoked lazily and repeatedly — e.g. once per
   * physical tenant by {@code OutboundConnectorManager} — so a supplier backed by a Spring {@code
   * BeanFactory#getBean(String, Class)} lookup yields a genuinely fresh instance per call for
   * prototype-scoped beans, while still returning the same shared instance for (the default)
   * singleton-scoped beans.
   */
  public record FunctionRegistration(
      Class<? extends OutboundConnectorFunction> type,
      Supplier<OutboundConnectorFunction> supplier) {}

  /** See {@link FunctionRegistration}; the equivalent for {@link OutboundConnectorProvider}. */
  public record ProviderRegistration(
      Class<? extends OutboundConnectorProvider> type,
      Supplier<OutboundConnectorProvider> supplier) {}

  private final Map<OutboundConnectorConfiguration, OutboundConnectorFunction>
      connectorInstanceCache = new ConcurrentHashMap<>();

  private final Function<String, String> propertyProvider;

  /**
   * Original public constructor, preserved unchanged for source/binary compatibility: callers
   * passing already-resolved {@link OutboundConnectorFunction}/{@link OutboundConnectorProvider}
   * instances (e.g. non-Spring runtimes, tests) continue to work exactly as before #6961, with each
   * instance wrapped in a constant-returning supplier (no fresh-instance-per-call capability). Use
   * {@link #fromRegistrations} instead when per-tenant instance isolation is required.
   */
  public DefaultOutboundConnectorFactory(
      ObjectMapper objectMapper,
      ValidationProvider validationProvider,
      List<OutboundConnectorFunction> constructorFunctions,
      List<OutboundConnectorProvider> constructorProviders,
      Function<String, String> propertyProvider) {
    this(
        objectMapper,
        validationProvider,
        constructorFunctions.stream()
            .map(f -> new FunctionRegistration(f.getClass(), () -> f))
            .toList(),
        constructorProviders.stream()
            .map(p -> new ProviderRegistration(p.getClass(), () -> p))
            .toList(),
        propertyProvider,
        RegistrationMarker.INSTANCE);
  }

  /**
   * Factory for callers that need genuine per-tenant instance isolation (#6961): each {@link
   * FunctionRegistration}/{@link ProviderRegistration}'s {@link Supplier} is invoked lazily and
   * repeatedly — e.g. once per physical tenant by {@code OutboundConnectorManager} — so a supplier
   * that creates a brand-new bean instance on every call (e.g. via {@code
   * AutowireCapableBeanFactory#createBean(Class)}) yields real isolation regardless of the bean's
   * declared Spring scope.
   *
   * <p>Exposed as a distinctly-named static factory rather than an overloaded constructor: {@code
   * List<FunctionRegistration>}/{@code List<ProviderRegistration>} erase to the same {@code List}
   * as {@code List<OutboundConnectorFunction>}/{@code List<OutboundConnectorProvider>}, so
   * overloading the public constructor would silently break already-compiled callers of the
   * original constructor (same erased signature, different expected element type — a {@code
   * ClassCastException} at runtime, not a compile error).
   */
  public static DefaultOutboundConnectorFactory fromRegistrations(
      ObjectMapper objectMapper,
      ValidationProvider validationProvider,
      List<FunctionRegistration> functionRegistrations,
      List<ProviderRegistration> providerRegistrations,
      Function<String, String> propertyProvider) {
    return new DefaultOutboundConnectorFactory(
        objectMapper,
        validationProvider,
        functionRegistrations,
        providerRegistrations,
        propertyProvider,
        RegistrationMarker.INSTANCE);
  }

  /**
   * Marker type used only to give the private registration-based constructor below a distinct
   * erased signature from the public instance-based constructor above (both would otherwise erase
   * to the same {@code (ObjectMapper, ValidationProvider, List, List, Function)}).
   */
  private enum RegistrationMarker {
    INSTANCE
  }

  private DefaultOutboundConnectorFactory(
      ObjectMapper objectMapper,
      ValidationProvider validationProvider,
      List<FunctionRegistration> constructorFunctionRegistrations,
      List<ProviderRegistration> constructorProviderRegistrations,
      Function<String, String> propertyProvider,
      RegistrationMarker marker) {
    this.propertyProvider = propertyProvider;
    List<OutboundConnectorConfiguration> envVarConfigurations = new ArrayList<>();
    Stream<ServiceLoader.Provider<OutboundConnectorFunction>> spiFunctions = Stream.empty();
    Stream<ServiceLoader.Provider<OutboundConnectorProvider>> spiProviders = Stream.empty();
    if (!DisabledConnectorEnvVarsConfig.isDiscoveryDisabled(ConnectorDirection.OUTBOUND)) {
      if (EnvVarsConnectorDiscovery.isOutboundConfigured()) {
        envVarConfigurations.addAll(EnvVarsConnectorDiscovery.discoverOutbound());
      } else {
        // Load outbound connector functions from SPI
        spiFunctions = SPIConnectorDiscovery.loadConnectorFunctions();
        spiProviders = SPIConnectorDiscovery.loadConnectorProviders();
      }
    }

    Function<OutboundConnectorProvider, OutboundConnectorFunction> fromProviderToFunction =
        p -> {
          ConnectorOperations connectorOperations =
              ConnectorOperations.from(p, objectMapper, validationProvider);
          return new OutboundConnectorOperationFunction(connectorOperations);
        };

    // Combine all configurations from different sources from least to most important
    List<OutboundConnectorConfiguration> allConfigs = new ArrayList<>();

    // 1. SPI discovered functions
    allConfigs.addAll(
        toConfigurations(spiFunctions, ServiceLoader.Provider::type, ServiceLoader.Provider::get));

    // 2. SPI discovered providers
    allConfigs.addAll(
        toConfigurations(
            spiProviders,
            ServiceLoader.Provider::type,
            spiProviderOfConnectorProvider ->
                fromProviderToFunction.apply(spiProviderOfConnectorProvider.get())));

    // 3. Constructor supplied functions
    allConfigs.addAll(
        toConfigurations(
            constructorFunctionRegistrations.stream(),
            FunctionRegistration::type,
            fr -> fr.supplier().get()));

    // 4. Constructor supplied providers
    allConfigs.addAll(
        toConfigurations(
            constructorProviderRegistrations.stream(),
            ProviderRegistration::type,
            pr -> fromProviderToFunction.apply(pr.supplier().get())));

    // 5. Env vars discovered configurations
    allConfigs.addAll(envVarConfigurations);

    // Use the base class method to create configurations map
    initializeConfigurations(allConfigs);
  }

  private <T> List<OutboundConnectorConfiguration> toConfigurations(
      Stream<T> elements, Function<T, OutboundConnectorFunction> instanceProvider) {
    return toConfigurations(elements, T::getClass, instanceProvider);
  }

  private <T> List<OutboundConnectorConfiguration> toConfigurations(
      Stream<T> elements,
      Function<T, Class<?>> typeProvider,
      Function<T, OutboundConnectorFunction> instanceProvider) {
    return elements
        .filter(e -> typeProvider.apply(e).isAnnotationPresent(OutboundConnector.class))
        .map(
            e -> {
              final OutboundConnector outboundConnector =
                  typeProvider.apply(e).getAnnotation(OutboundConnector.class);
              return toConfiguration(outboundConnector, () -> instanceProvider.apply(e));
            })
        .collect(Collectors.toList());
  }

  private OutboundConnectorConfiguration toConfiguration(
      OutboundConnector outboundConnector, Supplier<OutboundConnectorFunction> instanceProvider) {
    final var configurationOverrides =
        new ConnectorConfigurationOverrides(outboundConnector.name(), this.propertyProvider);

    return new OutboundConnectorConfiguration(
        outboundConnector.name(),
        outboundConnector.inputVariables(),
        configurationOverrides.typeOverride().orElse(outboundConnector.type()),
        instanceProvider,
        configurationOverrides.timeoutOverride().orElse(null));
  }

  @Override
  public OutboundConnectorFunction getInstance(String type) {
    return this.getActiveConfiguration(type)
        .map(this::getCachedInstance)
        .orElseThrow(
            () ->
                new NoSuchElementException(
                    "Outbound connector \"" + type + "\" is not registered"));
  }

  private OutboundConnectorFunction getCachedInstance(
      OutboundConnectorConfiguration configuration) {
    return connectorInstanceCache.computeIfAbsent(
        configuration, config -> config.instanceSupplier().get());
  }
}
