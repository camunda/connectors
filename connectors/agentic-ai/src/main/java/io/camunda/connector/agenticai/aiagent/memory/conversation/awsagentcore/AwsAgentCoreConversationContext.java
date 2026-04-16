/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import jakarta.annotation.Nullable;

/**
 * Conversation context for AWS AgentCore Memory storage.
 *
 * <p>Stores minimal state needed to identify and continue the conversation in AgentCore Memory.
 * Uses a branch-per-turn strategy to avoid split-brain issues: each agent turn writes new messages
 * to a fresh branch, and the branch name is stored in this context. If job completion fails after
 * writing, the orphaned branch is invisible to the next retry (which loads from the previous
 * branch).
 *
 * <p>The system message is stored separately in this context since AgentCore Memory doesn't support
 * storing SYSTEM role messages. It needs to be preserved across iterations and re-applied to
 * runtime memory on load.
 *
 * @param conversationId The conversation ID, used as sessionId in AgentCore.
 * @param memoryId The ID of the AgentCore Memory resource.
 * @param actorId The actor ID associated with this conversation.
 * @param branchName The branch name for the current conversation state. Null on the first turn
 *     (events on main timeline).
 * @param lastEventId The event ID of the last written event. Used as rootEventId when creating the
 *     next branch.
 * @param systemMessage System message that cannot be stored in AgentCore but needs to persist.
 */
@AgenticAiRecord
@JsonDeserialize(
    builder =
        AwsAgentCoreConversationContext.AwsAgentCoreConversationContextJacksonProxyBuilder.class)
public record AwsAgentCoreConversationContext(
    String conversationId,
    String memoryId,
    String actorId,
    @Nullable String branchName,
    @Nullable String lastEventId,
    @Nullable SystemMessage systemMessage)
    implements ConversationContext, AwsAgentCoreConversationContextBuilder.With {

  public static AwsAgentCoreConversationContextBuilder builder() {
    return AwsAgentCoreConversationContextBuilder.builder();
  }

  public static AwsAgentCoreConversationContextBuilder builder(String conversationId) {
    return builder().conversationId(conversationId);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class AwsAgentCoreConversationContextJacksonProxyBuilder
      extends AwsAgentCoreConversationContextBuilder {}
}
