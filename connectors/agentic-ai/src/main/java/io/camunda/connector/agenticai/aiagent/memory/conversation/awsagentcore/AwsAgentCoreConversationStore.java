/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore;

import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStore;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.AwsAgentCoreMemoryStorageConfiguration;
import java.util.Optional;
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
    final var config =
        Optional.ofNullable(executionContext.memory())
            .map(MemoryConfiguration::storage)
            .orElse(null);

    if (!(config instanceof AwsAgentCoreMemoryStorageConfiguration agentCoreConfig)) {
      throw new IllegalStateException(
          "Expected memory storage configuration to be of type AwsAgentCoreMemoryStorageConfiguration, but got: %s"
              .formatted(config != null ? config.getClass().getName() : "null"));
    }

    final BedrockAgentCoreClient client = clientFactory.createClient(agentCoreConfig);
    return new AwsAgentCoreConversationSession(agentCoreConfig, client, conversationMapper);
  }

  /** Factory interface for creating BedrockAgentCoreClient instances. */
  @FunctionalInterface
  public interface BedrockAgentCoreClientFactory {
    BedrockAgentCoreClient createClient(AwsAgentCoreMemoryStorageConfiguration config);
  }
}
