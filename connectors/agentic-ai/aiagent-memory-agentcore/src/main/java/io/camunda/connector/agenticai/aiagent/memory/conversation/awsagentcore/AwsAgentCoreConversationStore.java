/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore;

import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.api.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;

/**
 * Conversation store implementation for AWS AgentCore Memory.
 *
 * <p>This store persists conversation messages to AWS AgentCore Memory short-term storage, enabling
 * context-aware agent interactions across sessions.
 */
public class AwsAgentCoreConversationStore implements ConversationStore {

  public static final String TYPE = "aws-agentcore";

  private final BedrockAgentCoreClientFactory clientFactory;
  private final AwsAgentCoreConversationMapper conversationMapper;

  public AwsAgentCoreConversationStore(
      BedrockAgentCoreClientFactory clientFactory,
      AwsAgentCoreConversationMapper conversationMapper) {
    this.clientFactory = clientFactory;
    this.conversationMapper = conversationMapper;
  }

  @Override
  public String type() {
    return TYPE;
  }

  @Override
  public ConversationSession createSession(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    final var config = executionContext.configuration().memory().storage();

    if (!(config instanceof AwsAgentCoreConversationStoreConfiguration agentCoreConfig)) {
      throw new IllegalStateException(
          "Expected conversation store configuration to be of type AwsAgentCoreConversationStoreConfiguration, but got: %s"
              .formatted(config.getClass().getName()));
    }

    final BedrockAgentCoreClient client = clientFactory.createClient(agentCoreConfig);
    return new AwsAgentCoreConversationSession(agentCoreConfig, client, conversationMapper);
  }

  /** Factory interface for creating BedrockAgentCoreClient instances. */
  @FunctionalInterface
  public interface BedrockAgentCoreClientFactory {
    BedrockAgentCoreClient createClient(AwsAgentCoreConversationStoreConfiguration config);
  }
}
