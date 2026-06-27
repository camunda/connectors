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

/**
 * Factory that binds a concrete {@link SandboxConfiguration} subtype to a {@link SandboxProvider}
 * implementation.
 *
 * <p>Each provider adapter (T5 Daytona, T9 Docker, …) registers its own factory as a Spring bean.
 * {@link SandboxProviderRegistry} discovers all factories via the {@code
 * List<SandboxProviderFactory>} injection point so the registry itself never needs to know about
 * concrete providers.
 */
public interface SandboxProviderFactory {

  /**
   * Returns the {@link SandboxConfiguration} subtype this factory handles. Used by {@link
   * SandboxProviderRegistry} to select the right factory via an {@code instanceof} check.
   */
  Class<? extends SandboxConfiguration> configType();

  /**
   * Creates a {@link SandboxProvider} instance configured from {@code config}.
   *
   * @param config a non-null configuration instance whose runtime type matches {@link
   *     #configType()}
   */
  SandboxProvider create(SandboxConfiguration config);

  /**
   * Derives the {@link SandboxSpec} (workspace creation parameters) from {@code config}.
   *
   * @param config a non-null configuration instance whose runtime type matches {@link
   *     #configType()}
   */
  SandboxSpec specFor(SandboxConfiguration config);
}
