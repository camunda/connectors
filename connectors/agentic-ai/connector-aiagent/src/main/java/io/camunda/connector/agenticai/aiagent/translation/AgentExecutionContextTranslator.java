/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.translation;

import io.camunda.connector.agenticai.aiagent.agent.AgentSubProcessExecutionContext;
import io.camunda.connector.agenticai.aiagent.agent.AgentTaskExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.AdHocToolElement;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.request.AgentSubProcessRequestData;
import io.camunda.connector.agenticai.aiagent.request.AgentTaskRequestData;
import io.camunda.connector.agenticai.aiagent.request.ToolsConfiguration;
import io.camunda.connector.agenticai.tools.schema.processdefinition.ProcessDefinitionAdHocToolElementsResolver;
import io.camunda.connector.api.outbound.JobContext;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Builds the execution contexts of the agent from the connector's request records, so that the
 * contexts and the agent loop only see the domain model.
 */
public final class AgentExecutionContextTranslator {

  private AgentExecutionContextTranslator() {}

  public static AgentTaskExecutionContext task(
      JobContext jobContext,
      AgentTaskRequestData data,
      ChatModelConfiguration chatModel,
      ProcessDefinitionAdHocToolElementsResolver toolElementsResolver) {
    final var tools = Optional.ofNullable(data.tools());
    return new AgentTaskExecutionContext(
        jobContext,
        AgentConfigurationTranslator.translate(chatModel, data),
        data.context(),
        tools.map(ToolsConfiguration::toolCallResults).orElseGet(Collections::emptyList),
        tools.map(ToolsConfiguration::containerElementId).orElse(null),
        toolElementsResolver);
  }

  public static AgentSubProcessExecutionContext subProcess(
      JobContext jobContext,
      AgentSubProcessRequestData data,
      AgentContext agentContext,
      List<ToolCallResult> toolCallResults,
      List<AdHocToolElement> toolElements,
      ChatModelConfiguration chatModel) {
    final var includeAgentContext =
        data.response() != null && Boolean.TRUE.equals(data.response().includeAgentContext());
    return new AgentSubProcessExecutionContext(
        jobContext,
        AgentConfigurationTranslator.translate(chatModel, data),
        includeAgentContext,
        agentContext,
        toolCallResults,
        toolElements);
  }
}
