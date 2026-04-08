/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore;

import static io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationUtil.loadConversationContext;

import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSession;
import io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping.AwsAgentCoreConversationMapper;
import io.camunda.connector.agenticai.aiagent.memory.runtime.RuntimeMemory;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.AwsAgentCoreMemoryStorageConfiguration;
import io.camunda.connector.agenticai.model.message.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.*;

/**
 * Conversation session implementation for AWS AgentCore Memory.
 *
 * <p>Handles loading messages from AgentCore Memory into runtime memory and storing new messages
 * back to AgentCore Memory as conversational events.
 */
public class AwsAgentCoreConversationSession implements ConversationSession {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AwsAgentCoreConversationSession.class);

  private final AwsAgentCoreMemoryStorageConfiguration config;
  private final BedrockAgentCoreClient client;
  private final AwsAgentCoreConversationMapper mapper;

  private AwsAgentCoreConversationContext previousConversationContext;
  private int initialMessageCount = 0;

  public AwsAgentCoreConversationSession(
      AwsAgentCoreMemoryStorageConfiguration config,
      BedrockAgentCoreClient client,
      AgentExecutionContext executionContext,
      AwsAgentCoreConversationMapper mapper) {
    this.config = config;
    this.client = client;
    this.mapper = mapper;

    // executionContext currently not used by this session implementation, but kept for API symmetry
    // with other ConversationSession implementations.
    if (executionContext == null) {
      throw new IllegalArgumentException("executionContext must not be null");
    }
  }

  @Override
  public void loadIntoRuntimeMemory(AgentContext agentContext, RuntimeMemory memory) {
    previousConversationContext =
        loadConversationContext(agentContext, AwsAgentCoreConversationContext.class);

    // Restore system message first (it's stored in context, not in AgentCore)
    if (previousConversationContext != null
        && previousConversationContext.systemMessage() != null) {
      memory.addMessage(previousConversationContext.systemMessage());
    }

    final String sessionId = resolveSessionId(agentContext);
    final String branchName =
        previousConversationContext != null ? previousConversationContext.branchName() : null;
    final List<Message> messages = loadMessagesFromAgentCore(sessionId, branchName);

    initialMessageCount = messages.size();
    memory.addMessages(messages);

    LOGGER.debug(
        "Loaded {} messages from AgentCore Memory for session '{}' branch '{}' (plus {} system message from context)",
        messages.size(),
        sessionId,
        branchName != null ? branchName : "<main>",
        previousConversationContext != null && previousConversationContext.systemMessage() != null
            ? 1
            : 0);
  }

  @Override
  public AgentContext storeFromRuntimeMemory(AgentContext agentContext, RuntimeMemory memory) {
    validateConfigurationConsistency();

    final String sessionId = resolveSessionId(agentContext);
    final List<Message> allMessages = memory.allMessages();

    // Extract system message (only one) from storable messages
    // System message needs to be preserved in context since AgentCore doesn't support it
    final SystemMessage systemMessage =
        allMessages.stream()
            .filter(SystemMessage.class::isInstance)
            .map(SystemMessage.class::cast)
            .findFirst()
            .orElse(null);

    final List<Message> storableMessages =
        allMessages.stream().filter(this::isStorableMessage).toList();

    // New messages = everything after what was loaded from AgentCore
    final List<Message> newMessages =
        initialMessageCount < storableMessages.size()
            ? storableMessages.subList(initialMessageCount, storableMessages.size())
            : List.of();

    String newBranchName = null;
    String lastEventId =
        previousConversationContext != null ? previousConversationContext.lastEventId() : null;

    if (!newMessages.isEmpty()) {
      // Each turn writes to a new branch, forking from the last event of the previous turn.
      // This ensures that if job completion fails after storing, the orphaned branch is
      // invisible to the retry (which loads from the previous branch stored in context).
      newBranchName = UUID.randomUUID().toString();
      lastEventId = storeMessagesToAgentCore(sessionId, newMessages, newBranchName, lastEventId);
      LOGGER.debug(
          "Stored {} new messages to AgentCore Memory for session '{}' on branch '{}' ({} system message preserved in context)",
          newMessages.size(),
          sessionId,
          newBranchName,
          systemMessage != null ? 1 : 0);
    }

    final var conversationContextBuilder =
        previousConversationContext != null
            ? previousConversationContext.with()
            : AwsAgentCoreConversationContext.builder(sessionId);

    final var conversationContext =
        conversationContextBuilder
            .memoryId(config.memoryId())
            .actorId(config.actorId())
            .sessionId(sessionId)
            .branchName(
                newBranchName != null
                    ? newBranchName
                    : (previousConversationContext != null
                        ? previousConversationContext.branchName()
                        : null))
            .lastEventId(lastEventId)
            .systemMessage(systemMessage)
            .build();

    return agentContext.withConversation(conversationContext);
  }

  /**
   * Check if a message can be stored in AgentCore Memory.
   *
   * <p>AgentCore Memory only supports USER, ASSISTANT, and TOOL roles in conversational payloads.
   * System messages should NOT be stored in memory - they are applied at query time when calling
   * the LLM, not persisted as conversation history.
   *
   * @param message the message to check
   * @return true if the message can be stored, false otherwise
   */
  private boolean isStorableMessage(Message message) {
    return message instanceof UserMessage
        || message instanceof AssistantMessage
        || message instanceof ToolCallResultMessage;
  }

  /**
   * Validates that memoryId and actorId have not changed between iterations. Changing these would
   * silently write to a different memory resource while the context still references the old one.
   */
  private void validateConfigurationConsistency() {
    if (previousConversationContext == null) {
      return;
    }
    if (previousConversationContext.memoryId() != null
        && !previousConversationContext.memoryId().equals(config.memoryId())) {
      throw new IllegalStateException(
          "memoryId changed between iterations (was '%s', now '%s'). Changing the memory resource mid-conversation is not supported."
              .formatted(previousConversationContext.memoryId(), config.memoryId()));
    }
    if (previousConversationContext.actorId() != null
        && !previousConversationContext.actorId().equals(config.actorId())) {
      throw new IllegalStateException(
          "actorId changed between iterations (was '%s', now '%s'). Changing the actor mid-conversation is not supported."
              .formatted(previousConversationContext.actorId(), config.actorId()));
    }
  }

  private String resolveSessionId(AgentContext agentContext) {
    // Use existing session ID from context if available
    if (previousConversationContext != null && previousConversationContext.sessionId() != null) {
      return previousConversationContext.sessionId();
    }

    // Use conversation ID from agent context if available
    final var existingConversation = agentContext != null ? agentContext.conversation() : null;
    if (existingConversation != null && existingConversation.conversationId() != null) {
      return existingConversation.conversationId();
    }

    // Generate a new session ID
    return UUID.randomUUID().toString();
  }

  private List<Message> loadMessagesFromAgentCore(String sessionId, String branchName) {
    final var requestBuilder =
        ListEventsRequest.builder()
            .memoryId(config.memoryId())
            .actorId(config.actorId())
            .sessionId(sessionId)
            .includePayloads(true);

    if (branchName != null) {
      requestBuilder.filter(
          FilterInput.builder()
              .branch(BranchFilter.builder().name(branchName).includeParentBranches(true).build())
              .build());
    }

    final var request = requestBuilder.build();

    try {
      final List<Event> allEvents = new ArrayList<>();
      client.listEventsPaginator(request).events().forEach(allEvents::add);

      return allEvents.stream()
          .sorted(
              Comparator.comparing(
                      Event::eventTimestamp, Comparator.nullsLast(Comparator.naturalOrder()))
                  .thenComparing(Event::eventId, Comparator.nullsLast(Comparator.naturalOrder())))
          .flatMap(event -> mapper.fromEvent(event).stream())
          .toList();
    } catch (BedrockAgentCoreException e) {
      // Fail fast: this is a runtime configuration/permission/service issue and continuing silently
      // can cause duplicated history or incorrect agent behavior.
      LOGGER.error(
          "Failed to load events from AgentCore Memory for session '{}' (memoryId='{}', actorId='{}'): {}",
          sessionId,
          config.memoryId(),
          config.actorId(),
          e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage(),
          e);
      throw new IllegalStateException(
          "Failed to load conversation history from AgentCore Memory", e);
    } catch (Exception e) {
      LOGGER.error(
          "Unexpected error while loading events from AgentCore Memory for session '{}' (memoryId='{}', actorId='{}'): {}",
          sessionId,
          config.memoryId(),
          config.actorId(),
          e.getMessage(),
          e);
      throw new IllegalStateException(
          "Unexpected error while loading conversation history from AgentCore Memory", e);
    }
  }

  /**
   * Stores messages to AgentCore Memory on a new branch.
   *
   * @param sessionId the session ID
   * @param messages the new messages to store
   * @param branchName the branch name for this turn
   * @param rootEventId the event ID to fork from (null on first turn)
   * @return the event ID of the last written event
   */
  private String storeMessagesToAgentCore(
      String sessionId, List<Message> messages, String branchName, String rootEventId) {
    String lastEventId = rootEventId;
    final Branch branch =
        rootEventId != null
            ? Branch.builder().name(branchName).rootEventId(rootEventId).build()
            : null;

    for (int offset = 0; offset < messages.size(); offset++) {
      final Message message = messages.get(offset);
      final List<PayloadType> payloads = mapper.toPayloads(message);
      if (payloads.isEmpty()) {
        continue;
      }

      final var eventTimestamp = Instant.now();
      final var metadata = mapper.toAwsMetadata(message.metadata());
      final var requestBuilder =
          CreateEventRequest.builder()
              .memoryId(config.memoryId())
              .actorId(config.actorId())
              .sessionId(sessionId)
              .payload(payloads)
              .eventTimestamp(eventTimestamp)
              .clientToken(branchName + ":" + offset)
              .metadata(metadata);

      if (branch != null) {
        requestBuilder.branch(branch);
      }

      try {
        final var response = client.createEvent(requestBuilder.build());
        lastEventId = response.event().eventId();
      } catch (Exception e) {
        LOGGER.error(
            "Failed to store event to AgentCore Memory for session '{}' branch '{}': {}",
            sessionId,
            branchName,
            e.getMessage());
        throw new IllegalStateException(
            "Failed to store conversation event to AgentCore Memory", e);
      }
    }
    return lastEventId;
  }
}
