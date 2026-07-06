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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.azureopenai;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Inspects the chat completion requests the connector actually sent to WireMock at Azure OpenAI's
 * deployment-based URL. Wire shape is identical to {@code OpenAiCompletionsRecordedConversation} —
 * see {@code AzureOpenAiCompletionsChatModelStubs} for why this is deliberate, not duplication by
 * accident.
 */
public final class AzureOpenAiCompletionsRecordedConversation {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<RecordedChatRequest> requests;

  private AzureOpenAiCompletionsRecordedConversation(List<RecordedChatRequest> requests) {
    this.requests = requests;
  }

  /** Reads and parses all recorded chat completion requests, oldest first. */
  public static AzureOpenAiCompletionsRecordedConversation recorded() {
    final List<LoggedRequest> loggedRequests =
        new ArrayList<>(
            findAll(
                postRequestedFor(
                    urlPathEqualTo(AzureOpenAiCompletionsChatModelStubs.CHAT_COMPLETIONS_PATH))));

    loggedRequests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));

    final List<RecordedChatRequest> parsed =
        loggedRequests.stream()
            .map(LoggedRequest::getBodyAsString)
            .map(RecordedChatRequest::parse)
            .toList();

    return new AzureOpenAiCompletionsRecordedConversation(parsed);
  }

  public List<RecordedChatRequest> requests() {
    return requests;
  }

  public record RecordedMessage(
      String role,
      String content,
      List<JsonNode> contentParts,
      String toolCallId,
      List<RecordedToolCall> toolCalls) {

    public String textContent() {
      if (content != null) return content;
      return contentParts.stream()
          .filter(p -> "text".equals(p.path("type").asText()))
          .map(p -> p.path("text").asText())
          .collect(Collectors.joining());
    }

    public record RecordedToolCall(String id, String name, String argumentsJson) {}
  }

  public record RecordedResponseFormat(String type, Map<String, Object> jsonSchema) {}

  /** A single parsed Azure OpenAI chat completion request body. */
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
            "Failed to parse recorded Azure OpenAI chat request body: " + rawBody, e);
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

    public List<RecordedMessage> messages() {
      return messages;
    }

    public List<ToolDefinition> toolDefinitions() {
      return toolDefinitions;
    }

    public Optional<RecordedResponseFormat> responseFormat() {
      return Optional.ofNullable(responseFormat);
    }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ResponseFormatWire(
        String type, @JsonProperty("json_schema") Map<String, Object> jsonSchema) {

      RecordedResponseFormat toResponseFormat() {
        return new RecordedResponseFormat(type, jsonSchema);
      }
    }
  }
}
