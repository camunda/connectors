/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.sandbox.provider.SandboxProviderRegistry;
import io.camunda.connector.agenticai.sandbox.spi.SandboxHandle;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation: resolves provider via {@link SandboxProviderRegistry}. If an existing
 * {@link SandboxHandle} is present in agentContext properties under {@link #SANDBOX_HANDLE_KEY},
 * reconnects to the existing session; otherwise creates a new one.
 */
public class SandboxSessionFactoryImpl implements SandboxSessionFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(SandboxSessionFactoryImpl.class);

  /** Key under which the {@link SandboxHandle} is stored in {@link AgentContext#properties()}. */
  public static final String SANDBOX_HANDLE_KEY = "sandboxHandle";

  private final SandboxProviderRegistry providerRegistry;

  public SandboxSessionFactoryImpl(SandboxProviderRegistry providerRegistry) {
    this.providerRegistry = providerRegistry;
  }

  @Override
  public Optional<SandboxSession> openSession(
      AgentExecutionContext ctx, AgentContext agentContext) {
    var sandboxConfig = ctx.configuration().sandboxConfiguration();
    if (sandboxConfig.isEmpty()) {
      return Optional.empty();
    }
    var config = sandboxConfig.get();
    var providerOpt = providerRegistry.providerFor(config);
    if (providerOpt.isEmpty()) {
      LOGGER.warn(
          "No sandbox provider found for configuration type: {}",
          config.getClass().getSimpleName());
      return Optional.empty();
    }
    var provider = providerOpt.get();

    // Try to reconnect to existing session
    var existingHandle = agentContext.properties().get(SANDBOX_HANDLE_KEY);
    if (existingHandle instanceof SandboxHandle handle) {
      LOGGER.debug("Reconnecting to existing sandbox session: {}", handle.sessionId());
      try {
        return Optional.of(provider.connect(handle));
      } catch (Exception e) {
        LOGGER.warn(
            "Failed to reconnect to existing sandbox session {}, creating new one: {}",
            handle.sessionId(),
            e.getMessage());
      }
    }

    // Create a new session
    var specOpt = providerRegistry.specFor(config);
    if (specOpt.isEmpty()) {
      LOGGER.warn(
          "No sandbox spec found for configuration type: {}", config.getClass().getSimpleName());
      return Optional.empty();
    }
    LOGGER.debug("Creating new sandbox session");
    return Optional.of(provider.create(specOpt.get()));
  }
}
