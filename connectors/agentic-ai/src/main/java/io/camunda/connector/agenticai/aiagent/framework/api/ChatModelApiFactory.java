/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import java.util.Set;

/**
 * Stateless factory that produces per-job {@link ChatModelApi} instances for a single wire-protocol
 * family ({@code anthropic-messages}, {@code bedrock-converse}, {@code openai-responses}, {@code
 * openai-completions}, {@code google-genai}).
 *
 * <p>The {@link ChatModelApiRegistry} indexes factories by the {@link ProviderConfiguration#type
 * providerType} strings each factory claims via {@link #supportedProviderTypes} and dispatches by
 * exact match. {@link #apiFamily} is informational — used in logs and stream events.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Not yet wired into the runtime.
 *
 * @param <C> the {@link ProviderConfiguration} subtype this factory handles
 */
public interface ChatModelApiFactory<C extends ProviderConfiguration> {

  String apiFamily();

  Set<String> supportedProviderTypes();

  ChatModelApi create(C configuration);
}
