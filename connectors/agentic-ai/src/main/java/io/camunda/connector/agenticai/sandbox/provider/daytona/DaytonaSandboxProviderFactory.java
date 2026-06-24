/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider.daytona;

import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration;
import io.camunda.connector.agenticai.sandbox.provider.SandboxProviderFactory;
import io.camunda.connector.agenticai.sandbox.spi.SandboxProvider;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;

/**
 * {@link SandboxProviderFactory} that creates a {@link DaytonaSandboxProvider} from a {@link
 * DaytonaSandboxConfiguration}.
 *
 * <p>This factory is registered as a Spring bean in {@link
 * io.camunda.connector.agenticai.autoconfigure.AgenticAiConnectorsAutoConfiguration} and discovered
 * automatically by {@link io.camunda.connector.agenticai.sandbox.provider.SandboxProviderRegistry}
 * via its {@code List<SandboxProviderFactory>} injection point.
 */
public class DaytonaSandboxProviderFactory implements SandboxProviderFactory {

  @Override
  public Class<? extends SandboxConfiguration> configType() {
    return DaytonaSandboxConfiguration.class;
  }

  @Override
  public SandboxProvider create(SandboxConfiguration config) {
    DaytonaSandboxConfiguration cfg = (DaytonaSandboxConfiguration) config;
    return new DaytonaSandboxProvider(cfg.apiKey(), cfg.apiUrl());
  }

  @Override
  public SandboxSpec specFor(SandboxConfiguration config) {
    DaytonaSandboxConfiguration cfg = (DaytonaSandboxConfiguration) config;
    return new SandboxSpec(
        cfg.snapshot(),
        null,
        null,
        cfg.autoStopMinutes(),
        cfg.autoArchiveMinutes(),
        cfg.autoDeleteMinutes());
  }
}
