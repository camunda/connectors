/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.request.DocumentsConfiguration;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessV1Request;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessV2Request;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskV1Request;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskV2Request;
import io.camunda.connector.agenticai.aiagent.request.V1ToV2ProviderConfigurationMapper;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the execution context of both AI Agent flavors. The {@link
 * V1ToV2ProviderConfigurationMapper} maps the v1 provider configuration of a v1 request to its v2
 * equivalent. A v2 request keeps its provider configuration.
 */
public class AgentExecutionContextFactory
    implements AgentTaskExecutionContextFactory, AgentSubProcessExecutionContextFactory {

  /** The deprecated v1 connector gives a new agent instance no agent instance feature. */
  private static final List<String> V1_NEW_INSTANCE_FEATURES = List.of();

  private final ProcessDefinitionAdHocToolElementsResolver toolElementsResolver;
  private final V1ToV2ProviderConfigurationMapper providerConfigurationMapper;

  public AgentExecutionContextFactory(
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver,
      V1ToV2ProviderConfigurationMapper providerConfigurationMapper) {
    this.toolElementsResolver = toolElementsResolver;
    this.providerConfigurationMapper = providerConfigurationMapper;
  }

  @Override
  public AgentTaskExecutionContext taskContext(
      OutboundConnectorContext context, AgentTaskV1Request request) {
    return new AgentTaskExecutionContext(
        context.getJobContext(),
        context,
        request.data(),
        providerConfigurationMapper.map(request.provider()),
        toolElementsResolver,
        V1_NEW_INSTANCE_FEATURES);
  }

  @Override
  public AgentTaskExecutionContext taskContext(
      OutboundConnectorContext context, AgentTaskV2Request request) {
    return new AgentTaskExecutionContext(
        context.getJobContext(),
        context,
        request.data(),
        request.provider(),
        toolElementsResolver,
        v2NewInstanceFeatures(request.data().documents()));
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
        providerConfigurationMapper.map(request.provider()),
        V1_NEW_INSTANCE_FEATURES);
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
        request.provider(),
        v2NewInstanceFeatures(request.data().documents()));
  }

  /**
   * The features of a new agent instance of a v2 connector: the runtime tools, and the documents
   * feature when the agent uses document references.
   */
  private static List<String> v2NewInstanceFeatures(DocumentsConfiguration documents) {
    final var features = new ArrayList<String>();
    features.add(AgentFeatures.RUNTIME_TOOLS);
    if (documents.references()) {
      features.add(AgentFeatures.DOCUMENTS);
    }
    return List.copyOf(features);
  }
}
