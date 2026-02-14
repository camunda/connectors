/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.model.message.*;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockagentcore.model.Conversational;
import software.amazon.awssdk.services.bedrockagentcore.model.Event;
import software.amazon.awssdk.services.bedrockagentcore.model.MetadataValue;
import software.amazon.awssdk.services.bedrockagentcore.model.PayloadType;
import software.amazon.awssdk.services.bedrockagentcore.model.Role;

/**
 * Bidirectional mapper between Camunda internal Message types and AWS AgentCore Memory Event
 * payloads.
 *
 * <p>Implements the mapping strategy documented in README.md:
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

  private static final Logger LOGGER =
      LoggerFactory.getLogger(AwsAgentCoreConversationMapper.class);

  private static final TypeReference<List<ToolCall>> TOOL_CALLS_TYPE = new TypeReference<>() {};
  private static final TypeReference<List<ToolCallResult>> TOOL_CALL_RESULTS_TYPE =
      new TypeReference<>() {};

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
    return switch (message) {
      case UserMessage msg -> mapUserMessage(msg);
      case AssistantMessage msg -> mapAssistantMessage(msg);
      case ToolCallResultMessage msg -> mapToolCallResultMessage(msg);
      case SystemMessage msg ->
          throw new IllegalArgumentException(
              "SystemMessage should not be stored in AgentCore - store in context instead");
      default ->
          throw new IllegalArgumentException(
              "Unknown message type: " + message.getClass().getName());
    };
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

    // Extract metadata from event
    Map<String, Object> metadata = fromAwsMetadata(event.metadata());

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

    // Map each TextContent to conversational USER payload
    for (TextContent text : extractTextContent(message.content())) {
      payloads.add(createConversationalPayload(Role.USER, text.text()));
    }

    // Map each non-text Content to blob envelope
    for (Content content : extractNonTextContent(message.content())) {
      payloads.add(createContentBlobPayload(content));
    }

    return payloads;
  }

  private List<PayloadType> mapAssistantMessage(AssistantMessage message) {
    List<PayloadType> payloads = new ArrayList<>();

    // Map text content to conversational ASSISTANT
    for (TextContent text : extractTextContent(message.content())) {
      payloads.add(createConversationalPayload(Role.ASSISTANT, text.text()));
    }

    // Map non-text content to blob envelope
    for (Content content : extractNonTextContent(message.content())) {
      payloads.add(createContentBlobPayload(content));
    }

    // Map toolCalls to blob envelope
    if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
      payloads.add(createToolCallsBlobPayload(message.toolCalls()));
    }

    return payloads;
  }

  private List<PayloadType> mapToolCallResultMessage(ToolCallResultMessage message) {
    List<PayloadType> payloads = new ArrayList<>();

    // Create conversational TOOL with natural language summary
    String summary =
        message.results().stream()
            .map(ToolCallResult::content)
            .filter(Objects::nonNull)
            .map(Object::toString)
            .collect(Collectors.joining("\n"));

    if (!summary.isBlank()) {
      payloads.add(createConversationalPayload(Role.TOOL, summary));
    }

    // Create blob envelope with full structure
    payloads.add(createToolCallResultsBlobPayload(message.results()));

    return payloads;
  }

  // ==================== Payloads to Messages ====================

  private List<Message> extractMessagesFromPayloads(
      List<PayloadType> payloads, Map<String, Object> metadata) throws IOException {
    // Separate payloads by type
    List<Conversational> conversationals = new ArrayList<>();
    List<Document> blobs = new ArrayList<>();

    for (PayloadType payload : payloads) {
      if (payload.conversational() != null) {
        conversationals.add(payload.conversational());
      }
      if (payload.blob() != null) {
        blobs.add(payload.blob());
      }
    }

    // Determine message type from conversational roles
    if (conversationals.isEmpty() && !blobs.isEmpty()) {
      try {
        BlobEnvelope envelope = BlobEnvelope.fromDocument(blobs.get(0), objectMapper);
        if (envelope.is(BlobEnvelopeType.TOOL_CALLS)) {
          // AssistantMessage with toolCalls but no text content
          return List.of(reconstructAssistantMessage(conversationals, blobs, metadata));
        }
        // Blob exists but is not a recognized envelope type
        throw new AgentCoreMapperException(
            "Event has only blob payloads but blob is not a recognized envelope type", null);
      } catch (IOException e) {
        throw new AgentCoreMapperException("Failed to parse blob envelope: " + e.getMessage(), e);
      }
    }

    Role primaryRole = conversationals.get(0).role();
    return switch (primaryRole) {
      case USER -> List.of(reconstructUserMessage(conversationals, blobs, metadata));
      case ASSISTANT -> List.of(reconstructAssistantMessage(conversationals, blobs, metadata));
      case TOOL -> List.of(reconstructToolCallResultMessage(conversationals, blobs, metadata));
      case OTHER, UNKNOWN_TO_SDK_VERSION ->
          throw new IllegalStateException(
              "OTHER/UNKNOWN_TO_SDK_VERSION role is not supported in conversational payloads");
    };
  }

  private UserMessage reconstructUserMessage(
      List<Conversational> conversationals, List<Document> blobs, Map<String, Object> metadata)
      throws IOException {
    List<Content> content = new ArrayList<>();

    // Add text content from conversationals
    for (Conversational conv : conversationals) {
      String text = extractTextFromConversational(conv);
      if (text != null && !text.isBlank()) {
        content.add(TextContent.textContent(text));
      }
    }

    // Add non-text content from blobs
    content.addAll(parseContentFromBlobs(blobs));

    return UserMessage.builder().content(content).metadata(metadata).build();
  }

  private AssistantMessage reconstructAssistantMessage(
      List<Conversational> conversationals, List<Document> blobs, Map<String, Object> metadata)
      throws IOException {
    List<Content> content = new ArrayList<>();
    List<ToolCall> toolCalls = List.of();

    // Add text content from conversationals
    for (Conversational conv : conversationals) {
      String text = extractTextFromConversational(conv);
      if (text != null && !text.isBlank()) {
        content.add(TextContent.textContent(text));
      }
    }

    // Parse blobs - separate Content from ToolCalls
    for (Document blob : blobs) {
      try {
        BlobEnvelope envelope = BlobEnvelope.fromDocument(blob, objectMapper);

        if (envelope.is(BlobEnvelopeType.TOOL_CALLS)) {
          toolCalls = parseToolCallsFromEnvelope(envelope);
        } else if (envelope.is(BlobEnvelopeType.MESSAGE_CONTENT)) {
          content.add(parseContentFromEnvelope(envelope));
        } else {
          throw new AgentCoreMapperException(
              "Blob envelope type not recognized for AssistantMessage: " + envelope.blobType(),
              null);
        }
      } catch (IOException e) {
        throw new AgentCoreMapperException(
            "Failed to parse blob envelope for AssistantMessage: " + e.getMessage(), e);
      }
    }

    return AssistantMessage.builder()
        .content(content)
        .toolCalls(toolCalls)
        .metadata(metadata)
        .build();
  }

  private ToolCallResultMessage reconstructToolCallResultMessage(
      List<Conversational> conversationals, List<Document> blobs, Map<String, Object> metadata)
      throws IOException {
    List<ToolCallResult> results = List.of();

    // Try to parse full structure from blob envelope
    for (Document blob : blobs) {
      try {
        BlobEnvelope envelope = BlobEnvelope.fromDocument(blob, objectMapper);
        if (envelope.is(BlobEnvelopeType.TOOL_CALL_RESULTS)) {
          results = parseToolCallResultsFromEnvelope(envelope);
          break;
        } else {
          throw new AgentCoreMapperException(
              "Expected ToolCallResults blob envelope but found: " + envelope.blobType(), null);
        }
      } catch (IOException e) {
        throw new AgentCoreMapperException(
            "Failed to parse ToolCallResults blob envelope: " + e.getMessage(), e);
      }
    }

    // Fallback: create minimal result from conversational text
    if (results.isEmpty() && !conversationals.isEmpty()) {
      String text = extractTextFromConversational(conversationals.get(0));
      if (text != null && !text.isBlank()) {
        results = List.of(ToolCallResult.builder().content(text).build());
      }
    }

    return ToolCallResultMessage.builder().results(results).metadata(metadata).build();
  }

  // ==================== Helper Methods ====================

  private List<TextContent> extractTextContent(List<Content> content) {
    if (content == null) {
      return List.of();
    }
    return content.stream()
        .filter(c -> c instanceof TextContent)
        .map(c -> (TextContent) c)
        .toList();
  }

  private List<Content> extractNonTextContent(List<Content> content) {
    if (content == null) {
      return List.of();
    }
    return content.stream().filter(c -> !(c instanceof TextContent)).toList();
  }

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
    } catch (JsonProcessingException e) {
      throw new AgentCoreMapperException(
          "Failed to serialize Content to blob envelope: " + e.getMessage(), e);
    }
  }

  private PayloadType createToolCallsBlobPayload(List<ToolCall> toolCalls) {
    try {
      BlobEnvelope envelope = BlobEnvelope.forToolCalls(toolCalls, objectMapper);
      Document document = envelope.toDocument(objectMapper);
      return PayloadType.builder().blob(document).build();
    } catch (JsonProcessingException e) {
      throw new AgentCoreMapperException(
          "Failed to serialize ToolCalls to blob envelope: " + e.getMessage(), e);
    }
  }

  private PayloadType createToolCallResultsBlobPayload(List<ToolCallResult> results) {
    try {
      BlobEnvelope envelope = BlobEnvelope.forToolCallResults(results, objectMapper);
      Document document = envelope.toDocument(objectMapper);
      return PayloadType.builder().blob(document).build();
    } catch (JsonProcessingException e) {
      throw new AgentCoreMapperException(
          "Failed to serialize ToolCallResults to blob envelope: " + e.getMessage(), e);
    }
  }

  private String extractTextFromConversational(Conversational conversational) {
    if (conversational.content() == null) {
      return null;
    }
    return conversational.content().text();
  }

  private List<Content> parseContentFromBlobs(List<Document> blobs) throws IOException {
    List<Content> contentList = new ArrayList<>();

    for (Document blob : blobs) {
      try {
        BlobEnvelope envelope = BlobEnvelope.fromDocument(blob, objectMapper);
        if (envelope.is(BlobEnvelopeType.MESSAGE_CONTENT)) {
          contentList.add(parseContentFromEnvelope(envelope));
        } else {
          throw new AgentCoreMapperException(
              "Expected MessageContent blob envelope but found: " + envelope.blobType(), null);
        }
      } catch (IOException e) {
        throw new AgentCoreMapperException(
            "Failed to parse MessageContent blob envelope: " + e.getMessage(), e);
      }
    }

    return contentList;
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
   * Convert Message metadata to AWS AgentCore MetadataValue map.
   *
   * <p>AWS AgentCore only supports string values in metadata. Non-string values are converted to
   * JSON strings. Null values are skipped.
   *
   * @param metadata the message metadata (may be null or empty)
   * @return AWS MetadataValue map (empty if metadata is null or empty)
   */
  public Map<String, MetadataValue> toAwsMetadata(Map<String, Object> metadata) {
    if (metadata == null || metadata.isEmpty()) {
      return Map.of();
    }

    Map<String, MetadataValue> awsMetadata = new HashMap<>();
    for (Map.Entry<String, Object> entry : metadata.entrySet()) {
      if (entry.getValue() != null) {
        String stringValue = convertToString(entry.getValue());
        awsMetadata.put(entry.getKey(), MetadataValue.fromStringValue(stringValue));
      }
    }
    return awsMetadata;
  }

  /**
   * Convert AWS AgentCore MetadataValue map to Message metadata.
   *
   * @param awsMetadata the AWS metadata (may be null or empty)
   * @return Message metadata map (empty if awsMetadata is null or empty)
   */
  public Map<String, Object> fromAwsMetadata(Map<String, MetadataValue> awsMetadata) {
    if (awsMetadata == null || awsMetadata.isEmpty()) {
      return Map.of();
    }

    Map<String, Object> metadata = new HashMap<>();
    for (Map.Entry<String, MetadataValue> entry : awsMetadata.entrySet()) {
      if (entry.getValue() != null && entry.getValue().stringValue() != null) {
        metadata.put(entry.getKey(), entry.getValue().stringValue());
      }
    }
    return metadata;
  }

  private String convertToString(Object value) {
    if (value instanceof String) {
      return (String) value;
    }
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      // Fallback to toString for non-serializable objects
      return value.toString();
    }
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
