/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.model.message.UserMessage;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

/**
 * Bidirectional mapper between Camunda internal Message types and AWS AgentCore Memory Event
 * payloads.
 *
 * <p>Mapping strategy:
 *
 * <ul>
 *   <li>TextContent → Conversational payloads (flow into long-term memory)
 *   <li>Non-text Content → Blob envelopes with MESSAGE_CONTENT type
 *   <li>ToolCalls → Blob envelopes with TOOL_CALLS type
 *   <li>ToolCallResults → Conversational TOOL (summary) + Blob envelope (full structure)
 *   <li>SystemMessage → Not mapped (stored in context separately)
 * </ul>
 *
 * <p>All failures throw exceptions - no silent swallowing of errors.
 */
public class AwsAgentCoreConversationMapper {

  private static final TypeReference<List<ToolCall>> TOOL_CALLS_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<ToolCallResult>> TOOL_CALL_RESULTS_TYPE =
      new TypeReference<>() {};
  private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {};

  static final String PROPERTY_USER_NAME = "userName";

  private final ObjectMapper objectMapper;

  public AwsAgentCoreConversationMapper(ObjectMapper objectMapper) {
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
  }

  /**
   * Convert an internal Message to AWS AgentCore PayloadType list.
   *
   * <p>One Message maps to one Event, which may contain multiple payloads.
   *
   * @param message the message to convert
   * @return list of payloads for the event
   * @throws IllegalArgumentException if message type is not supported or is SystemMessage
   * @throws AgentCoreMapperException if serialization fails
   */
  public List<PayloadType> toPayloads(Message message) {
    List<PayloadType> payloads =
        switch (message) {
          case UserMessage msg -> mapUserMessage(msg);
          case AssistantMessage msg -> mapAssistantMessage(msg);
          case ToolCallResultMessage msg -> mapToolCallResultMessage(msg);
          case SystemMessage ignored ->
              throw new IllegalArgumentException(
                  "SystemMessage should not be stored in AgentCore - store in context instead");
          default ->
              throw new IllegalArgumentException(
                  "Unknown message type: " + message.getClass().getName());
        };

    // collect internal properties that need round-tripping via the metadata blob
    var properties = extractProperties(message);
    boolean hasMetadata = message.metadata() != null && !message.metadata().isEmpty();
    boolean hasProperties = properties != null && !properties.isEmpty();

    if (hasMetadata || hasProperties) {
      var metadata = hasMetadata ? message.metadata() : Map.<String, Object>of();
      List<PayloadType> withMetadata = new ArrayList<>(payloads);
      withMetadata.add(createMetadataBlobPayload(metadata, properties));
      return withMetadata;
    }
    return payloads;
  }

  /**
   * Extract Messages from an AWS AgentCore Event.
   *
   * <p>Typically one Event maps to one Message, but this returns a list for flexibility.
   *
   * @param event the event to extract messages from
   * @return list of extracted messages (may be empty if event has no payloads)
   * @throws AgentCoreMapperException if deserialization fails
   */
  public List<Message> fromEvent(Event event) {
    List<PayloadType> payloads = event.payload();
    if (payloads == null || payloads.isEmpty()) {
      return List.of();
    }

    // metadata is stored as a blob envelope, not in AWS event metadata
    Map<String, Object> metadata = Map.of();

    try {
      return extractMessagesFromPayloads(payloads, metadata);
    } catch (IOException e) {
      throw new AgentCoreMapperException(
          "Failed to extract messages from event: " + e.getMessage(), e);
    }
  }

  // ==================== Message to Payloads ====================

  private List<PayloadType> mapUserMessage(UserMessage message) {
    List<PayloadType> payloads = new ArrayList<>();
    mapContentInOrder(payloads, message.content(), Role.USER);
    return payloads;
  }

  private List<PayloadType> mapAssistantMessage(AssistantMessage message) {
    List<PayloadType> payloads = new ArrayList<>();
    mapContentInOrder(payloads, message.content(), Role.ASSISTANT);

    // toolCalls are appended after content (not part of the content list)
    if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
      payloads.add(createToolCallsBlobPayload(message.toolCalls()));
    }

    return payloads;
  }

  /**
   * Maps content items to payloads preserving their original order. TextContent becomes
   * conversational payloads, non-text Content becomes blob envelopes.
   */
  private void mapContentInOrder(List<PayloadType> payloads, List<Content> content, Role role) {
    if (content == null) {
      return;
    }
    for (Content item : content) {
      if (item instanceof TextContent text) {
        payloads.add(createConversationalPayload(role, text.text()));
      } else {
        payloads.add(createContentBlobPayload(item));
      }
    }
  }

  private List<PayloadType> mapToolCallResultMessage(ToolCallResultMessage message) {
    List<PayloadType> payloads = new ArrayList<>();

    // create conversational TOOL with natural language summary
    String summary =
        message.results().stream()
            .map(ToolCallResult::content)
            .filter(Objects::nonNull)
            .map(Object::toString)
            .collect(Collectors.joining("\n"));

    if (!summary.isBlank()) {
      payloads.add(createConversationalPayload(Role.TOOL, summary));
    }

    // create blob envelope with full structure
    payloads.add(createToolCallResultsBlobPayload(message.results()));

    return payloads;
  }

  // ==================== Payloads to Messages ====================

  /**
   * Extracts messages from payloads by processing them in order, preserving the original content
   * interleaving between conversational and blob payloads.
   *
   * <p>Special blob types (metadata, toolCalls, toolCallResults) are extracted separately. Content
   * payloads (conversational text and messageContent blobs) are collected in their original order.
   */
  private List<Message> extractMessagesFromPayloads(
      List<PayloadType> payloads, Map<String, Object> metadata) throws IOException {
    Role messageRole = null;
    List<Content> content = new ArrayList<>();
    List<ToolCall> toolCalls = List.of();
    List<ToolCallResult> toolCallResults = null;
    Map<String, Object> properties = null;

    for (PayloadType payload : payloads) {
      if (payload.conversational() != null) {
        Conversational conv = payload.conversational();
        if (messageRole == null) {
          messageRole = conv.role();
        }
        if (messageRole == Role.TOOL) {
          // TOOL conversational payload is a summary for long-term memory extraction —
          // skip it if we have a blob with the full structure (handled below)
          continue;
        }
        String text = extractTextFromConversational(conv);
        if (text != null && !text.isBlank()) {
          content.add(TextContent.textContent(text));
        }
      } else if (payload.blob() != null) {
        BlobEnvelope envelope = BlobEnvelope.fromDocument(payload.blob(), objectMapper);

        if (envelope.is(BlobEnvelopeType.MESSAGE_METADATA)) {
          metadata = envelope.parseData(METADATA_TYPE, objectMapper);
          properties = envelope.parseProperties(METADATA_TYPE, objectMapper);
        } else if (envelope.is(BlobEnvelopeType.TOOL_CALLS)) {
          toolCalls = parseToolCallsFromEnvelope(envelope);
          if (messageRole == null) {
            messageRole = Role.ASSISTANT; // toolCalls-only AssistantMessage (no text)
          }
        } else if (envelope.is(BlobEnvelopeType.TOOL_CALL_RESULTS)) {
          toolCallResults = parseToolCallResultsFromEnvelope(envelope);
          if (messageRole == null) {
            messageRole = Role.TOOL; // toolCallResults-only event (no conversational summary)
          }
        } else if (envelope.is(BlobEnvelopeType.MESSAGE_CONTENT)) {
          content.add(parseContentFromEnvelope(envelope));
        } else {
          throw new AgentCoreMapperException(
              "Unrecognized blob envelope type: " + envelope.blobType(), null);
        }
      }
    }

    if (messageRole == null) {
      return List.of();
    }

    return switch (messageRole) {
      case USER -> {
        var builder = UserMessage.builder().content(content).metadata(metadata);
        if (properties != null && properties.get(PROPERTY_USER_NAME) instanceof String name) {
          builder.name(name);
        }
        yield List.of(builder.build());
      }
      case ASSISTANT ->
          List.of(
              AssistantMessage.builder()
                  .content(content)
                  .toolCalls(toolCalls)
                  .metadata(metadata)
                  .build());
      case TOOL -> {
        if (toolCallResults == null) {
          throw new AgentCoreMapperException(
              "TOOL event is missing required 'camunda.toolCallResults' blob envelope", null);
        }
        yield List.of(
            ToolCallResultMessage.builder().results(toolCallResults).metadata(metadata).build());
      }
      case OTHER, UNKNOWN_TO_SDK_VERSION ->
          throw new IllegalStateException(
              "OTHER/UNKNOWN_TO_SDK_VERSION role is not supported in conversational payloads");
    };
  }

  // ==================== Helper Methods ====================

  private PayloadType createConversationalPayload(Role role, String text) {
    Conversational conversational =
        Conversational.builder()
            .role(role)
            .content(software.amazon.awssdk.services.bedrockagentcore.model.Content.fromText(text))
            .build();
    return PayloadType.builder().conversational(conversational).build();
  }

  private PayloadType createContentBlobPayload(Content content) {
    try {
      BlobEnvelope envelope = BlobEnvelope.forContent(content, objectMapper);
      Document document = envelope.toDocument(objectMapper);
      return PayloadType.builder().blob(document).build();
    } catch (Exception e) {
      throw new AgentCoreMapperException(
          "Failed to serialize Content to blob envelope: " + e.getMessage(), e);
    }
  }

  private PayloadType createToolCallsBlobPayload(List<ToolCall> toolCalls) {
    try {
      BlobEnvelope envelope = BlobEnvelope.forToolCalls(toolCalls, objectMapper);
      Document document = envelope.toDocument(objectMapper);
      return PayloadType.builder().blob(document).build();
    } catch (Exception e) {
      throw new AgentCoreMapperException(
          "Failed to serialize ToolCalls to blob envelope: " + e.getMessage(), e);
    }
  }

  private PayloadType createToolCallResultsBlobPayload(List<ToolCallResult> results) {
    try {
      BlobEnvelope envelope = BlobEnvelope.forToolCallResults(results, objectMapper);
      Document document = envelope.toDocument(objectMapper);
      return PayloadType.builder().blob(document).build();
    } catch (Exception e) {
      throw new AgentCoreMapperException(
          "Failed to serialize ToolCallResults to blob envelope: " + e.getMessage(), e);
    }
  }

  private PayloadType createMetadataBlobPayload(
      Map<String, Object> metadata, Map<String, Object> properties) {
    try {
      BlobEnvelope envelope = BlobEnvelope.forMetadata(metadata, properties, objectMapper);
      Document document = envelope.toDocument(objectMapper);
      return PayloadType.builder().blob(document).build();
    } catch (Exception e) {
      throw new AgentCoreMapperException(
          "Failed to serialize metadata to blob envelope: " + e.getMessage(), e);
    }
  }

  private String extractTextFromConversational(Conversational conversational) {
    if (conversational.content() == null) {
      return null;
    }
    return conversational.content().text();
  }

  private Content parseContentFromEnvelope(BlobEnvelope envelope) throws IOException {
    return envelope.parseData(Content.class, objectMapper);
  }

  private List<ToolCall> parseToolCallsFromEnvelope(BlobEnvelope envelope) throws IOException {
    return envelope.parseData(TOOL_CALLS_TYPE, objectMapper);
  }

  private List<ToolCallResult> parseToolCallResultsFromEnvelope(BlobEnvelope envelope)
      throws IOException {
    return envelope.parseData(TOOL_CALL_RESULTS_TYPE, objectMapper);
  }

  /**
   * Extracts internal properties from a message that need round-tripping via the metadata blob's
   * properties section. Returns null if there are no properties to store.
   */
  private Map<String, Object> extractProperties(Message message) {
    if (message instanceof UserMessage userMsg && userMsg.name() != null) {
      return Map.of(PROPERTY_USER_NAME, userMsg.name());
    }
    return null;
  }

  /**
   * Exception thrown when mapping between Messages and AgentCore payloads fails.
   *
   * <p>This is a runtime exception to avoid cluttering method signatures, but represents
   * non-recoverable errors that should be handled at the session level.
   */
  public static class AgentCoreMapperException extends RuntimeException {
    public AgentCoreMapperException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
