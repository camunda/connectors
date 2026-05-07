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
 * registered {@link ChatModelApiFactory} beans, indexed by the strings each factory advertises via
 * {@link ChatModelApiFactory#providerTypes()}; lookup is exact-match on the configuration's
 * provider type.
 *
 * <p>Unknown provider types fail fast — there is no factory to resolve, so requests can never
 * start.
 *
 * <p>Part of the ADR-005 Phase 1 SPI scaffolding. Wired by ChatClientImpl, dispatched via
 * ChatModelApiRegistry.
 */
public interface ChatModelApiRegistry {

  ChatModelApi resolve(ProviderConfiguration configuration);
}
