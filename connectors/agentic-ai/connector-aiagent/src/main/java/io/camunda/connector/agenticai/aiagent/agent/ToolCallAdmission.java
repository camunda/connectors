/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Admits the tool calls of an assistant message from the model, before the message enters the
 * conversation: each call gets the kind of the offered tool of its name, and a rejection when no
 * tool has the name or a document parameter does not name a known document. The decision is
 * persisted with the call and never made again.
 */
public class ToolCallAdmission {

  /**
   * Admits the calls of the message.
   *
   * @param offeredTools the tools offered to the model in this job
   * @param agentContext the agent context the message is answered with
   * @return the message with every call admitted or rejected; a message without calls as it is
   */
  public AssistantMessage admit(
      AssistantMessage message, List<ToolDefinition> offeredTools, AgentContext agentContext) {
    if (!message.hasToolCalls()) {
      return message;
    }
    final Map<String, ToolDefinition> definitionsByName =
        offeredTools.stream()
            .collect(
                Collectors.toMap(
                    ToolDefinition::name, Function.identity(), (first, second) -> first));
    return message.withToolCalls(
        message.toolCalls().stream()
            .map(call -> admit(call, definitionsByName.get(call.name()), agentContext))
            .toList());
  }

  private static ToolCall admit(
      ToolCall call, @Nullable ToolDefinition definition, AgentContext agentContext) {
    if (definition == null) {
      return call.withRejection("The tool '%s' does not exist.".formatted(call.name()));
    }
    final var admitted = call.withKind(definition.kind());
    if (DocumentParameterResolver.check(agentContext, call, definition)
        instanceof DocumentParameterResolver.Resolution.Invalid invalid) {
      return admitted.withRejection(invalid.message());
    }
    return admitted;
  }
}
