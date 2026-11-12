/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapper;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;

/**
 * Builds the execution context of both AI Agent flavors. A connector function depends on the
 * interface of its flavor, {@link AgentTaskExecutionContextFactory} or {@link
 * AgentSubProcessExecutionContextFactory}.
 */
public interface AgentExecutionContextFactory
    extends AgentTaskExecutionContextFactory, AgentSubProcessExecutionContextFactory {

  /**
   * Returns the default factory. It maps a v1 provider configuration with {@code
   * providerConfigurationMapper} when {@code rewriteV1ProviderConfigToV2} is {@code true}, and
   * keeps it otherwise.
   */
  static AgentExecutionContextFactory create(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      V1ToV2ProviderConfigurationMapper providerConfigurationMapper,
      boolean rewriteV1ProviderConfigToV2) {
    return new AgentExecutionContextFactoryImpl(
        toolElementsResolver, providerConfigurationMapper, rewriteV1ProviderConfigToV2);
  }
}
