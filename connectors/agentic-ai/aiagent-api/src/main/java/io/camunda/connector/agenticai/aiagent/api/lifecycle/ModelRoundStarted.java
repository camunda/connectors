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
 * A call to the chat model starts.
 *
 * @param round the 1-based number of the call within the job; unlike the iteration key it does not
 *     count across the lifetime of the agent, and a provider continuation produces further rounds
 */
@Experimental
public record ModelRoundStarted(
    int round,
    ChatModelConfiguration chatModel,
    List<Message> messages,
    List<ToolDefinition> toolDefinitions) {}
