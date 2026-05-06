/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.api;

import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;

/**
 * Resolves a {@link ChatModelApi} for a given {@link ProviderConfiguration}. Composed of all
 * registered {@link ChatModelApiFactory} beans; factory selection is driven by the configuration's
 * discriminator.
 *
 * <p>Unknown api families fail fast at validation rather than runtime — there is no factory bean
 * to resolve, so requests can never start.
 *
 * <p>Part of the ADR-004 Phase 1 SPI scaffolding. Not yet wired into the runtime.
 */
public interface ChatModelApiRegistry {

  ChatModelApi resolve(ProviderConfiguration configuration);
}
