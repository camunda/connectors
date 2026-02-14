/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.awsagentcore.mapping;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.agenticai.model.message.content.Content;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import java.io.IOException;
import java.util.List;
import software.amazon.awssdk.core.document.Document;

/**
 * Type-safe wrapper for structured blob payloads in AWS AgentCore Memory.
 *
 * <p>All non-conversational structured data (toolCalls, toolCallResults, Content objects) is
 * wrapped in a versioned envelope with a blobType discriminator for consistent deserialization.
 *
 * <p>Envelope structure:
 *
 * <pre>{@code
 * {
 *   "blobType": "camunda.<typeIdentifier>",
 *   "version": 1,
 *   "<dataKey>": { ... }
 * }
 * }</pre>
 */
public record BlobEnvelope(String blobType, int version, JsonNode data) {

  public static final int CURRENT_VERSION = 1;

  private static final String FIELD_BLOB_TYPE = "blobType";
  private static final String FIELD_VERSION = "version";
  private static final String FIELD_TOOL_CALLS = "toolCalls";
  private static final String FIELD_RESULTS = "results";
  private static final String FIELD_CONTENT = "content";

  /**
   * Create an envelope for a ToolCall array.
   *
   * @param toolCalls the tool calls to wrap
   * @param mapper the ObjectMapper to use for serialization
   * @return the envelope
   * @throws JsonProcessingException if serialization fails
   */
  public static BlobEnvelope forToolCalls(List<ToolCall> toolCalls, ObjectMapper mapper)
      throws JsonProcessingException {
    JsonNode data = mapper.valueToTree(toolCalls);
    ObjectNode envelope = mapper.createObjectNode();
    envelope.put(FIELD_BLOB_TYPE, BlobEnvelopeType.TOOL_CALLS.getBlobType());
    envelope.put(FIELD_VERSION, CURRENT_VERSION);
    envelope.set(FIELD_TOOL_CALLS, data);
    return new BlobEnvelope(BlobEnvelopeType.TOOL_CALLS.getBlobType(), CURRENT_VERSION, envelope);
  }

  /**
   * Create an envelope for a ToolCallResult array.
   *
   * @param results the tool call results to wrap
   * @param mapper the ObjectMapper to use for serialization
   * @return the envelope
   * @throws JsonProcessingException if serialization fails
   */
  public static BlobEnvelope forToolCallResults(List<ToolCallResult> results, ObjectMapper mapper)
      throws JsonProcessingException {
    JsonNode data = mapper.valueToTree(results);
    ObjectNode envelope = mapper.createObjectNode();
    envelope.put(FIELD_BLOB_TYPE, BlobEnvelopeType.TOOL_CALL_RESULTS.getBlobType());
    envelope.put(FIELD_VERSION, CURRENT_VERSION);
    envelope.set(FIELD_RESULTS, data);
    return new BlobEnvelope(
        BlobEnvelopeType.TOOL_CALL_RESULTS.getBlobType(), CURRENT_VERSION, envelope);
  }

  /**
   * Create an envelope for a Content object.
   *
   * @param content the content to wrap (preserves Content's native type discriminator)
   * @param mapper the ObjectMapper to use for serialization
   * @return the envelope
   * @throws JsonProcessingException if serialization fails
   */
  public static BlobEnvelope forContent(Content content, ObjectMapper mapper)
      throws JsonProcessingException {
    JsonNode data = mapper.valueToTree(content);
    ObjectNode envelope = mapper.createObjectNode();
    envelope.put(FIELD_BLOB_TYPE, BlobEnvelopeType.MESSAGE_CONTENT.getBlobType());
    envelope.put(FIELD_VERSION, CURRENT_VERSION);
    envelope.set(FIELD_CONTENT, data);
    return new BlobEnvelope(
        BlobEnvelopeType.MESSAGE_CONTENT.getBlobType(), CURRENT_VERSION, envelope);
  }

  /**
   * Parse a blob Document as a BlobEnvelope.
   *
   * @param blob the AWS SDK Document from a blob payload
   * @param mapper the ObjectMapper to use for parsing
   * @return the parsed envelope
   * @throws IOException if parsing fails or the blob is not a valid envelope
   */
  public static BlobEnvelope fromDocument(Document blob, ObjectMapper mapper) throws IOException {
    String json = blob.asString();
    JsonNode root = mapper.readTree(json);

    if (!root.has(FIELD_BLOB_TYPE)) {
      throw new IOException("Blob does not contain 'blobType' field");
    }
    if (!root.has(FIELD_VERSION)) {
      throw new IOException("Blob does not contain 'version' field");
    }

    String blobType = root.get(FIELD_BLOB_TYPE).asText();
    int version = root.get(FIELD_VERSION).asInt();

    return new BlobEnvelope(blobType, version, root);
  }

  /**
   * Check if this envelope matches the given type.
   *
   * @param type the type to check
   * @return true if the blobType matches
   */
  public boolean is(BlobEnvelopeType type) {
    return type.getBlobType().equals(blobType);
  }

  /**
   * Parse the data field using a TypeReference.
   *
   * @param typeRef the type reference for deserialization
   * @param mapper the ObjectMapper to use
   * @param <T> the target type
   * @return the deserialized data
   * @throws IOException if deserialization fails
   */
  public <T> T parseData(TypeReference<T> typeRef, ObjectMapper mapper) throws IOException {
    JsonNode dataNode = extractDataNode();
    return mapper.convertValue(dataNode, typeRef);
  }

  /**
   * Parse the data field using a Class.
   *
   * @param clazz the class for deserialization
   * @param mapper the ObjectMapper to use
   * @param <T> the target type
   * @return the deserialized data
   * @throws IOException if deserialization fails
   */
  public <T> T parseData(Class<T> clazz, ObjectMapper mapper) throws IOException {
    JsonNode dataNode = extractDataNode();
    return mapper.treeToValue(dataNode, clazz);
  }

  /**
   * Convert this envelope to an AWS SDK Document.
   *
   * @param mapper the ObjectMapper to use for serialization
   * @return the Document
   * @throws JsonProcessingException if serialization fails
   */
  public Document toDocument(ObjectMapper mapper) throws JsonProcessingException {
    String json = mapper.writeValueAsString(data);
    return Document.fromString(json);
  }

  /**
   * Extract the appropriate data node based on envelope type.
   *
   * @return the data node
   * @throws IOException if the expected field is missing
   */
  private JsonNode extractDataNode() throws IOException {
    if (is(BlobEnvelopeType.TOOL_CALLS)) {
      if (!data.has(FIELD_TOOL_CALLS)) {
        throw new IOException("ToolCalls envelope missing 'toolCalls' field");
      }
      return data.get(FIELD_TOOL_CALLS);
    } else if (is(BlobEnvelopeType.TOOL_CALL_RESULTS)) {
      if (!data.has(FIELD_RESULTS)) {
        throw new IOException("ToolCallResults envelope missing 'results' field");
      }
      return data.get(FIELD_RESULTS);
    } else if (is(BlobEnvelopeType.MESSAGE_CONTENT)) {
      if (!data.has(FIELD_CONTENT)) {
        throw new IOException("MessageContent envelope missing 'content' field");
      }
      return data.get(FIELD_CONTENT);
    } else {
      throw new IOException("Unknown envelope type: " + blobType);
    }
  }
}
