/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider;

import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration;
import io.camunda.connector.agenticai.sandbox.spi.SandboxProvider;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import java.util.List;
import java.util.Optional;

/**
 * Resolves a {@link SandboxConfiguration} to the matching {@link SandboxProvider} by delegating to
 * registered {@link SandboxProviderFactory} instances.
 *
 * <p>Factories are discovered via Spring's {@code List<SandboxProviderFactory>} injection — each
 * provider adapter (Daytona T5, Docker T9, …) registers its own factory bean. The registry itself
 * is provider-agnostic and never changes when new providers are added.
 */
public class SandboxProviderRegistry {

  private final List<SandboxProviderFactory> factories;

  public SandboxProviderRegistry(List<SandboxProviderFactory> factories) {
    this.factories = List.copyOf(factories);
  }

  /**
   * Finds the factory whose {@link SandboxProviderFactory#configType()} is assignable from the
   * runtime type of {@code config}, then creates and returns a provider.
   *
   * @return an Optional containing the created provider, or empty if no factory handles this config
   *     type
   */
  public Optional<SandboxProvider> providerFor(SandboxConfiguration config) {
    return findFactory(config).map(f -> f.create(config));
  }

  /**
   * Derives the {@link SandboxSpec} for {@code config} via the matching factory.
   *
   * @return an Optional containing the spec, or empty if no factory handles this config type
   */
  public Optional<SandboxSpec> specFor(SandboxConfiguration config) {
    return findFactory(config).map(f -> f.specFor(config));
  }

  private Optional<SandboxProviderFactory> findFactory(SandboxConfiguration config) {
    return factories.stream().filter(f -> f.configType().isInstance(config)).findFirst();
  }
}
