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
import io.camunda.connector.agenticai.aiagent.model.request.MemoryStorageConfiguration.AwsAgentCoreMemoryStorageConfiguration;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.Branch;
import software.amazon.awssdk.services.bedrockagentcore.model.BranchFilter;
import software.amazon.awssdk.services.bedrockagentcore.model.CreateEventRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.FilterInput;
import software.amazon.awssdk.services.bedrockagentcore.model.ListEventsRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.MetadataValue;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;

/**
 * Conversation session implementation for AWS AgentCore Memory.
 *
 * <p>Handles loading messages from AgentCore Memory into runtime memory and storing new messages
 * back to AgentCore Memory as conversational events.
 */
public class AwsAgentCoreConversationSession implements ConversationSession {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AwsAgentCoreConversationSession.class);

  private static final String MAIN_TIMELINE = "main";
  private static final String EVENT_METADATA_SEQ = "seq";

  private final AwsAgentCoreMemoryStorageConfiguration config;
  private final BedrockAgentCoreClient client;
  private final AwsAgentCoreConversationMapper mapper;

  private AwsAgentCoreConversationContext previousConversationContext;
  private String sessionId;
  private String branchName;
  private String lastEventId;
  private int initialMessageCount = 0;

  public AwsAgentCoreConversationSession(
      AwsAgentCoreMemoryStorageConfiguration config,
      BedrockAgentCoreClient client,
      AwsAgentCoreConversationMapper mapper) {
    this.config = config;
    this.client = client;
    this.mapper = mapper;
  }

  @Override
  public void loadIntoRuntimeMemory(AgentContext agentContext, RuntimeMemory memory) {
    previousConversationContext =
        loadConversationContext(agentContext, AwsAgentCoreConversationContext.class);

    validateConfigurationConsistency();

    if (previousConversationContext != null) {
      sessionId = previousConversationContext.conversationId();
      branchName = previousConversationContext.branchName();
      lastEventId = previousConversationContext.lastEventId();
      if (previousConversationContext.systemMessage() != null) {
        memory.addMessage(previousConversationContext.systemMessage());
      }
    } else {
      sessionId = UUID.randomUUID().toString();
    }

    final List<Message> messages = loadMessagesFromAgentCore(sessionId, branchName);
    initialMessageCount = messages.size();
    memory.addMessages(messages);

    LOGGER.debug(
        "Loaded {} messages from AgentCore Memory for session '{}' branch '{}'",
        messages.size(),
        sessionId,
        branchName != null ? branchName : "<main>");
  }

  @Override
  public AgentContext storeFromRuntimeMemory(AgentContext agentContext, RuntimeMemory memory) {
    final List<Message> allMessages = memory.allMessages();

    // Extract system message — needs to be preserved in context since AgentCore doesn't support it
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

    if (!newMessages.isEmpty()) {
      // Branch-per-turn strategy: each turn after the first writes to a new branch, forking from
      // the last event of the previous turn. This ensures that if job completion fails after
      // storing, the orphaned branch is invisible to the retry (which loads from the previous
      // branch stored in context). The first turn writes to the main timeline (no branch) since
      // there is no prior event to fork from.
      if (lastEventId != null) {
        branchName = UUID.randomUUID().toString();
      }
      lastEventId = storeMessagesToAgentCore(sessionId, newMessages, branchName, lastEventId);
      LOGGER.debug(
          "Stored {} new messages to AgentCore Memory for session '{}' on branch '{}'",
          newMessages.size(),
          sessionId,
          branchName != null ? branchName : "<main>");
    }

    final var conversationContextBuilder =
        previousConversationContext != null
            ? previousConversationContext.with()
            : AwsAgentCoreConversationContext.builder(sessionId)
                .memoryId(config.memoryId())
                .actorId(config.actorId());

    return agentContext.withConversation(
        conversationContextBuilder
            .branchName(branchName)
            .lastEventId(lastEventId)
            .systemMessage(systemMessage)
            .build());
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
    if (!previousConversationContext.memoryId().equals(config.memoryId())) {
      throw new IllegalStateException(
          "memoryId changed between iterations (was '%s', now '%s'). Changing the memory resource mid-conversation is not supported."
              .formatted(previousConversationContext.memoryId(), config.memoryId()));
    }
    if (!previousConversationContext.actorId().equals(config.actorId())) {
      throw new IllegalStateException(
          "actorId changed between iterations (was '%s', now '%s'). Changing the actor mid-conversation is not supported."
              .formatted(previousConversationContext.actorId(), config.actorId()));
    }
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
                  .thenComparing(
                      AwsAgentCoreConversationSession::extractSeq,
                      Comparator.nullsLast(Comparator.naturalOrder())))
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

      final var requestBuilder =
          CreateEventRequest.builder()
              .memoryId(config.memoryId())
              .actorId(config.actorId())
              .sessionId(sessionId)
              .payload(payloads)
              .clientToken((branchName != null ? branchName : MAIN_TIMELINE) + ":" + offset)
              .metadata(
                  Map.of(
                      EVENT_METADATA_SEQ, MetadataValue.fromStringValue(String.valueOf(offset))));

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

  private static Integer extractSeq(Event event) {
    if (event.metadata() == null) {
      return null;
    }
    var seqValue = event.metadata().get(EVENT_METADATA_SEQ);
    if (seqValue == null || seqValue.stringValue() == null) {
      return null;
    }
    try {
      return Integer.parseInt(seqValue.stringValue());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
