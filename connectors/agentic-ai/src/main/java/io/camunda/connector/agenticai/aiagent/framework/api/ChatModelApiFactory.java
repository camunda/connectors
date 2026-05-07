/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;

/**
 * Stateless factory that produces per-job {@link ChatModelApi} instances for a single {@link
 * ProviderConfiguration#providerType()} discriminator. The bridge for a multi-provider framework
 * (e.g. LangChain4j) is registered as one factory bean per discriminator it handles.
 *
 * <p>The {@link ChatModelApiRegistry} indexes factories by {@link #providerType()} and dispatches
 * by exact match. Two factories claiming the same discriminator fail at startup; user overrides
 * happen via {@code @ConditionalOnMissingBean} on the built-in bean rather than silent shadowing.
 *
 * <p>{@link #apiFamily()} is informational telemetry (logs, {@link
 * io.camunda.connector.agenticai.aiagent.framework.api.event.ChatModelEvent.StartEvent}) — not used
 * for routing. {@link #configurationType()} acts as a defensive runtime check before the registry's
 * unchecked cast — a friendlier error than {@link ClassCastException} when a factory is
 * accidentally registered against the wrong discriminator.
 *
 * <p>Part of the ADR-005 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
 *
 * @param <C> the {@link ProviderConfiguration} subtype this factory handles
 */
public interface ChatModelApiFactory<C extends ProviderConfiguration> {

  String providerType();

  String apiFamily();

  Class<C> configurationType();

  ChatModelApi create(C configuration);
}
