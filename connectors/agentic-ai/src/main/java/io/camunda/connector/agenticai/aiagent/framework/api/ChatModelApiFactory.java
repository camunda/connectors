/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;

/**
 * Stateless factory that produces per-job {@link ChatModelApi} instances for a single
 * wire-protocol family ({@code anthropic-messages}, {@code bedrock-converse}, {@code
 * openai-responses}, {@code openai-completions}, {@code google-genai}).
 *
 * <p>One factory bean per family. The {@link ChatModelApiRegistry} routes a {@link
 * ProviderConfiguration} to the correct factory at request time using {@link #apiFamily} and
 * {@link #configurationType} as discriminators.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Not yet wired into the runtime.
 *
 * @param <C> the {@link ProviderConfiguration} subtype this factory handles
 */
public interface ChatModelApiFactory<C extends ProviderConfiguration> {

  String apiFamily();

  Class<C> configurationType();

  ChatModelApi create(C configuration);
}
