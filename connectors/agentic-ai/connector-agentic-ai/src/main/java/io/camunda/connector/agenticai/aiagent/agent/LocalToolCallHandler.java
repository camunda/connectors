/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether the tool calls of an assistant message are answered by the agent itself. The
 * process only completes a tool call batch when it has a result for every call, so a batch is
 * answered either fully here or fully by the process.
 */
public interface LocalToolCallHandler {

  /**
   * Returns the answers when the batch is answered locally, or empty when every call proceeds to
   * the process.
   */
  Optional<LocalToolCallAnswers> handle(AgentConversation conversation, List<ToolCall> toolCalls);

  /**
   * The answers of a locally answered batch.
   *
   * @param results one result per tool call, in the order of the calls
   * @param returnDocumentIds the documents the answers select for the final response of the agent
   */
  record LocalToolCallAnswers(List<ToolCallResult> results, List<String> returnDocumentIds) {
    public LocalToolCallAnswers {
      results = List.copyOf(results);
      returnDocumentIds = List.copyOf(returnDocumentIds);
    }
  }
}
