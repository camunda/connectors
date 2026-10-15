/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

/**
 * Maps a v1 {@code ProviderConfiguration} to the equivalent native v2 {@code
 * ProviderConfiguration}, so v1 agent jobs can run against the native providers.
 */
public interface V1ToV2ProviderConfigurationMapper {

  io.camunda.connector.agenticai.aiagent.model.request.v2.ProviderConfiguration map(
      io.camunda.connector.agenticai.aiagent.model.request.v1.ProviderConfiguration source);
}
