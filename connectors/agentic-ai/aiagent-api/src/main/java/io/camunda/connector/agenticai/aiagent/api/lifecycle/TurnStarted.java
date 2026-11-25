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
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.List;

/**
 * A turn starts: the chat model is about to be called. Reported after the lease-fenced agent
 * instance write that precedes the first turn of the job succeeded. Carries complete content,
 * including prompts and tool results.
 *
 * @param iterationKey the iteration key of the turn
 * @param chatModel the chat model the turn calls
 * @param messages the messages sent to the model: the window of the conversation, with the
 *     documents expanded
 * @param toolDefinitions the tools the model can call
 * @param inputMessages the input the turn adds to the conversation; empty for a turn that continues
 *     a response the provider paused
 * @param toolExecutions the tool call results among the input, with the calls they answer
 */
@Experimental
public record TurnStarted(
    int iterationKey,
    ChatModelConfiguration chatModel,
    List<Message> messages,
    List<ToolDefinition> toolDefinitions,
    List<Message> inputMessages,
    List<ToolExecution> toolExecutions) {}
