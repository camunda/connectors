/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessV1Request;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessV2Request;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskV1Request;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskV2Request;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapper;
import io.camunda.connector.agenticai.aiagent.request.v1.ProviderConfiguration;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.api.outbound.OutboundConnectorContext;

/**
 * The default {@link AgentExecutionContextFactory}. A v1 request keeps its v1 provider
 * configuration when {@code rewriteV1ProviderConfigToV2} is {@code false}. When it is {@code true},
 * the {@link V1ToV2ProviderConfigurationMapper} maps the v1 provider configuration to its v2
 * equivalent. A v2 request keeps its provider configuration.
 */
class AgentExecutionContextFactoryImpl implements AgentExecutionContextFactory {

  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private final V1ToV2ProviderConfigurationMapper providerConfigurationMapper;
  private final boolean rewriteV1ProviderConfigToV2;

  AgentExecutionContextFactoryImpl(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      V1ToV2ProviderConfigurationMapper providerConfigurationMapper,
      boolean rewriteV1ProviderConfigToV2) {
    this.toolElementsResolver = toolElementsResolver;
    this.providerConfigurationMapper = providerConfigurationMapper;
    this.rewriteV1ProviderConfigToV2 = rewriteV1ProviderConfigToV2;
  }

  @Override
  public AgentTaskExecutionContext taskContext(
      OutboundConnectorContext context, AgentTaskV1Request request) {
    return new AgentTaskExecutionContext(
        context.getJobContext(),
        context,
        request.data(),
        v1ChatModel(request.provider()),
        toolElementsResolver);
  }

  @Override
  public AgentTaskExecutionContext taskContext(
      OutboundConnectorContext context, AgentTaskV2Request request) {
    return new AgentTaskExecutionContext(
        context.getJobContext(), context, request.data(), request.provider(), toolElementsResolver);
  }

  @Override
  public AgentSubProcessExecutionContext subProcessContext(
      OutboundConnectorContext context, AgentSubProcessV1Request request) {
    return new AgentSubProcessExecutionContext(
        context.getJobContext(),
        context,
        request.data(),
        request.agentContext(),
        request.toolCallResults(),
        request.toolElements(),
        v1ChatModel(request.provider()));
  }

  @Override
  public AgentSubProcessExecutionContext subProcessContext(
      OutboundConnectorContext context, AgentSubProcessV2Request request) {
    return new AgentSubProcessExecutionContext(
        context.getJobContext(),
        context,
        request.data(),
        request.agentContext(),
        request.toolCallResults(),
        request.toolElements(),
        request.provider());
  }

  private ChatModelConfiguration v1ChatModel(ProviderConfiguration provider) {
    return rewriteV1ProviderConfigToV2 ? providerConfigurationMapper.map(provider) : provider;
  }
}
