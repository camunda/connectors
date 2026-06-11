/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.agenticai.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Inspects the chat completion requests the connector actually sent to WireMock.
 *
 * <p>Each model call produces one recorded request.
 */
public final class OpenAiCompletionsRecordedConversation {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<RecordedChatRequest> requests;

  private OpenAiCompletionsRecordedConversation(List<RecordedChatRequest> requests) {
    this.requests = requests;
  }

  /** Reads and parses all recorded {@code POST /v1/chat/completions} requests, oldest first. */
  public static OpenAiCompletionsRecordedConversation recorded() {
    final List<LoggedRequest> loggedRequests =
        new ArrayList<>(
            findAll(
                postRequestedFor(
                    urlPathEqualTo(OpenAiCompletionsChatModelStubs.CHAT_COMPLETIONS_PATH))));

    // WireMock does not guarantee ordering across versions; sort chronologically
    loggedRequests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));

    final List<RecordedChatRequest> parsed =
        loggedRequests.stream()
            .map(LoggedRequest::getBodyAsString)
            .map(RecordedChatRequest::parse)
            .toList();

    return new OpenAiCompletionsRecordedConversation(parsed);
  }

  /** Number of model calls, equivalent to {@code chatRequestCaptor.getAllValues().size()}. */
  public int modelCallCount() {
    return requests.size();
  }

  public List<RecordedChatRequest> requests() {
    return requests;
  }

  /** The most recent request, equivalent to {@code chatRequestCaptor.getValue()}. */
  public RecordedChatRequest lastRequest() {
    if (requests.isEmpty()) {
      throw new IllegalStateException("No chat completion requests were recorded");
    }
    return requests.getLast();
  }

  /**
   * A parsed message from the {@code messages} array of a chat completion request.
   *
   * <p>{@code content} is non-null when the message carries a plain text string. {@code
   * contentParts} is non-empty when the message carries a multimodal content array (e.g. document
   * uploads). {@code toolCallId} is non-null for tool-result messages. {@code toolCalls} is
   * non-empty for assistant messages that include tool calls.
   */
  public record RecordedMessage(
      String role,
      String content,
      List<JsonNode> contentParts,
      String toolCallId,
      List<RecordedToolCall> toolCalls) {

    /** Returns text content regardless of format (plain string or first text part in array). */
    public String textContent() {
      if (content != null) return content;
      return contentParts.stream()
          .filter(p -> "text".equals(p.path("type").asText()))
          .map(p -> p.path("text").asText())
          .collect(Collectors.joining());
    }

    public record RecordedToolCall(String id, String name, String argumentsJson) {}
  }

  /** The parsed {@code response_format} object from a chat completion request. */
  public record RecordedResponseFormat(String type, Map<String, Object> jsonSchema) {}

  /** A single parsed OpenAI-compatible chat completion request body. */
  public static final class RecordedChatRequest {

    static RecordedChatRequest parse(String rawBody) {
      try {
        var wireBody = OBJECT_MAPPER.readValue(rawBody, RequestBodyWire.class);

        var messages = wireBody.messages().stream().map(MessageWire::toRecordedMessage).toList();
        var toolDefinitions = wireBody.tools().stream().map(ToolWire::toToolDefinition).toList();
        var responseFormat =
            Optional.ofNullable(wireBody.responseFormat)
                .map(ResponseFormatWire::toResponseFormat)
                .orElse(null);

        return new RecordedChatRequest(messages, toolDefinitions, responseFormat);
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to parse recorded chat request body: " + rawBody, e);
      }
    }

    private final List<RecordedMessage> messages;
    private final List<ToolDefinition> toolDefinitions;
    private final RecordedResponseFormat responseFormat;

    private RecordedChatRequest(
        List<RecordedMessage> messages,
        List<ToolDefinition> toolDefinitions,
        RecordedResponseFormat responseFormat) {
      this.messages = messages;
      this.toolDefinitions = toolDefinitions;
      this.responseFormat = responseFormat;
    }

    /** The ordered {@code messages} array sent to the model. */
    public List<RecordedMessage> messages() {
      return messages;
    }

    /** Tool function names in the order they were declared. */
    public List<String> toolNames() {
      return toolDefinitions().stream().map(ToolDefinition::name).toList();
    }

    /** Tool definitions in the order they were declared. */
    public List<ToolDefinition> toolDefinitions() {
      return toolDefinitions;
    }

    /** The parsed response format, or empty if the request did not include one. */
    public Optional<RecordedResponseFormat> responseFormat() {
      return Optional.ofNullable(responseFormat);
    }

    /** Wire format for the full chat completion request body. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RequestBodyWire(
        List<MessageWire> messages,
        List<ToolWire> tools,
        @JsonProperty("response_format") ResponseFormatWire responseFormat) {

      private RequestBodyWire {
        messages = messages == null ? Collections.emptyList() : messages;
        tools = tools == null ? Collections.emptyList() : tools;
      }
    }

    /** Wire format for a single chat message. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MessageWire(
        String role,
        JsonNode content,
        @JsonProperty("tool_call_id") String toolCallId,
        @JsonProperty("tool_calls") List<ToolCallWire> toolCalls) {

      @JsonIgnoreProperties(ignoreUnknown = true)
      record ToolCallWire(String id, ToolCallFunctionWire function) {
        record ToolCallFunctionWire(String name, String arguments) {}
      }

      RecordedMessage toRecordedMessage() {
        // content is polymorphic: plain string, null, or multipart array
        final JsonNode contentNode = content;
        final String content;
        final List<JsonNode> contentParts;
        if (contentNode == null || contentNode.isNull()) {
          content = null;
          contentParts = List.of();
        } else if (contentNode.isTextual()) {
          content = contentNode.asText();
          contentParts = List.of();
        } else if (contentNode.isArray()) {
          content = null;
          final List<JsonNode> parts = new ArrayList<>();
          contentNode.forEach(parts::add);
          contentParts = parts;
        } else {
          content = null;
          contentParts = List.of();
        }

        final List<RecordedMessage.RecordedToolCall> toolCalls =
            toolCalls() == null
                ? List.of()
                : toolCalls().stream()
                    .map(
                        tc ->
                            new RecordedMessage.RecordedToolCall(
                                tc.id(), tc.function().name(), tc.function().arguments()))
                    .toList();

        return new RecordedMessage(role(), content, contentParts, toolCallId(), toolCalls);
      }
    }

    /** Wire format for a single entry in the {@code tools} array. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ToolWire(ToolFunctionWire function) {

      @JsonIgnoreProperties(ignoreUnknown = true)
      record ToolFunctionWire(String name, String description, Map<String, Object> parameters) {}

      ToolDefinition toToolDefinition() {
        return ToolDefinition.builder()
            .name(function.name())
            .description(function.description())
            .inputSchema(
                function.parameters() != null
                    ? function.parameters()
                    : Map.of("type", "object", "properties", Map.of(), "required", List.of()))
            .build();
      }
    }

    /** Wire format for the {@code response_format} field. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseFormatWire(
        String type, @JsonProperty("json_schema") Map<String, Object> jsonSchema) {

      RecordedResponseFormat toResponseFormat() {
        return new RecordedResponseFormat(type, jsonSchema);
      }
    }
  }
}
