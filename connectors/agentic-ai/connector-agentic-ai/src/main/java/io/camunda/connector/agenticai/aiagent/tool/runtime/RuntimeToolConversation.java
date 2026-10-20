/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceKey;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What a runtime tool can read about the conversation of the agent while it runs, as of the call.
 * The view is read-only: a tool changes the agent state through the {@link
 * RuntimeToolResult#withContextUpdate context updates} of its result.
 *
 * @param agentContext the agent context, including the changes the earlier calls made. Its {@link
 *     AgentContext#properties() properties} are the free-form state of the agent.
 * @param messages the messages the model reads as of the call: the system message, if any, and the
 *     turns within the context window, the current turn included
 */
public record RuntimeToolConversation(AgentContext agentContext, List<Message> messages) {

  public RuntimeToolConversation {
    messages = List.copyOf(messages);
  }

  /** The key of the agent instance, or {@code null} if there is none yet. */
  public @Nullable AgentInstanceKey agentInstanceKey() {
    return AgentInstanceKey.from(agentContext.metadata());
  }
}
