/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.lifecycle;

import io.camunda.connector.agenticai.aiagent.api.Experimental;
import io.camunda.connector.agenticai.aiagent.model.chatmodel.ChatModelConfiguration;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A turn starts: its input is composed and the model is about to be called. Carries complete
 * content, including prompts and tool results.
 *
 * @param toolExecutions the tool results consumed by this turn
 * @param toolCallsIssuedAt when the tool calls that these results answer were issued, derived from
 *     the time-ordered id of the assistant message that issued them. Absent when there are no tool
 *     results or the id is not time-based.
 */
@Experimental
public record TurnStarted(
    int iterationKey,
    List<ToolExecution> toolExecutions,
    @Nullable Instant toolCallsIssuedAt,
    List<Message> inputMessages,
    @Nullable SystemMessage systemMessage,
    ChatModelConfiguration chatModel,
    List<ToolDefinition> toolDefinitions) {}
