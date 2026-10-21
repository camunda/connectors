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
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A turn starts: its input is composed, the lease-fenced agent instance write of the turn start
 * succeeded, and the model is about to be called. Carries complete content, including prompts and
 * tool results.
 *
 * @param toolExecutions the tool results consumed by this turn
 */
@Experimental
public record TurnStarted(
    int iterationKey,
    List<ToolExecution> toolExecutions,
    List<Message> inputMessages,
    @Nullable SystemMessage systemMessage,
    ChatModelConfiguration chatModel,
    List<ToolDefinition> toolDefinitions) {}
