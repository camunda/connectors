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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.anthropic;

import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Inspects the {@code POST /v1/messages} requests the connector actually sent to WireMock.
 *
 * <p>Anthropic's wire format differs from OpenAI's in two ways this parser normalizes away so that
 * the shared {@code ProviderWireFormatExpectedMessage} DSL can be reused unchanged:
 *
 * <ul>
 *   <li>The system prompt is a top-level {@code system} field, not a message with role {@code
 *       system} in the {@code messages} array — it is synthesized as a leading {@code system}
 *       message here.
 *   <li>Tool results for a turn are carried as {@code tool_result} content blocks inside a single
 *       {@code user}-role message (Anthropic requires all tool results for a turn in one message),
 *       not as separate messages per result as OpenAI does — each block is split out into its own
 *       {@code tool}-role message here.
 * </ul>
 */
public final class AnthropicMessagesRecordedConversation {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<RecordedChatRequest> requests;

  private AnthropicMessagesRecordedConversation(List<RecordedChatRequest> requests) {
    this.requests = requests;
  }

  /** Reads and parses all recorded {@code POST /v1/messages} requests, oldest first. */
  public static AnthropicMessagesRecordedConversation recorded() {
    final List<LoggedRequest> loggedRequests =
        new ArrayList<>(
            findAll(
                postRequestedFor(urlPathEqualTo(AnthropicMessagesChatModelStubs.MESSAGES_PATH))));

    loggedRequests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));

    final List<RecordedChatRequest> parsed =
        loggedRequests.stream()
            .map(LoggedRequest::getBodyAsString)
            .map(RecordedChatRequest::parse)
            .toList();

    return new AnthropicMessagesRecordedConversation(parsed);
  }

  public List<RecordedChatRequest> requests() {
    return requests;
  }

  public record RecordedToolCall(String id, String name, String argumentsJson) {}

  /** A content block, in Anthropic's own wire shape: {@code kind} is the block's {@code type}. */
  public record ContentBlock(String kind, String text) {}

  public record RecordedMessage(
      String role,
      List<ContentBlock> contentParts,
      List<RecordedToolCall> toolCalls,
      String toolCallId) {}

  public record RecordedResponseFormat(String type, Map<String, Object> jsonSchema) {}

  /** A single parsed Anthropic Messages request body. */
  public static final class RecordedChatRequest {

    static RecordedChatRequest parse(String rawBody) {
      try {
        final var root = OBJECT_MAPPER.readTree(rawBody);
        final List<RecordedMessage> messages = new ArrayList<>();

        final var systemText = extractSystemText(root.path("system"));
        if (systemText != null) {
          messages.add(
              new RecordedMessage(
                  "system", List.of(new ContentBlock("text", systemText)), List.of(), null));
        }

        root.path("messages").forEach(message -> messages.addAll(toRecordedMessages(message)));

        final var tools =
            StreamSupport.stream(root.path("tools").spliterator(), false)
                .map(RecordedChatRequest::toToolDefinition)
                .toList();

        final var responseFormat = extractResponseFormat(root.path("output_config"));

        return new RecordedChatRequest(messages, tools, responseFormat);
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to parse recorded Anthropic messages request body: " + rawBody, e);
      }
    }

    private static String extractSystemText(JsonNode system) {
      if (system.isMissingNode() || system.isNull()) {
        return null;
      }
      if (system.isTextual()) {
        return system.asText();
      }
      return StreamSupport.stream(system.spliterator(), false)
          .filter(block -> "text".equals(block.path("type").asText()))
          .map(block -> block.path("text").asText())
          .collect(Collectors.joining());
    }

    private static List<RecordedMessage> toRecordedMessages(JsonNode message) {
      final var role = message.path("role").asText();
      final var content = message.path("content");

      final boolean isToolResultMessage =
          StreamSupport.stream(content.spliterator(), false)
              .anyMatch(block -> "tool_result".equals(block.path("type").asText()));

      if ("user".equals(role) && isToolResultMessage) {
        final List<RecordedMessage> toolResults = new ArrayList<>();
        content.forEach(
            block -> {
              if ("tool_result".equals(block.path("type").asText())) {
                toolResults.add(
                    new RecordedMessage(
                        "tool",
                        List.of(new ContentBlock("text", toolResultText(block.path("content")))),
                        List.of(),
                        block.path("tool_use_id").asText()));
              }
            });
        return toolResults;
      }

      // Content parts exclude tool_use blocks - those become toolCalls() instead.
      final var contentParts =
          StreamSupport.stream(content.spliterator(), false)
              .filter(block -> !"tool_use".equals(block.path("type").asText()))
              .map(
                  block -> {
                    final var kind = block.path("type").asText();
                    return new ContentBlock(
                        kind, "text".equals(kind) ? block.path("text").asText() : null);
                  })
              .toList();

      final var toolCalls =
          StreamSupport.stream(content.spliterator(), false)
              .filter(block -> "tool_use".equals(block.path("type").asText()))
              .map(
                  block ->
                      new RecordedToolCall(
                          block.path("id").asText(),
                          block.path("name").asText(),
                          block.path("input").toString()))
              .toList();

      return List.of(new RecordedMessage(role, contentParts, toolCalls, null));
    }

    private static String toolResultText(JsonNode content) {
      if (content.isTextual()) {
        return content.asText();
      }
      return StreamSupport.stream(content.spliterator(), false)
          .filter(block -> "text".equals(block.path("type").asText()))
          .map(block -> block.path("text").asText())
          .collect(Collectors.joining());
    }

    private static ToolDefinition toToolDefinition(JsonNode tool) {
      final var inputSchema = OBJECT_MAPPER.convertValue(tool.path("input_schema"), Map.class);
      return ToolDefinition.builder()
          .name(tool.path("name").asText())
          .description(tool.path("description").asText())
          .inputSchema(inputSchema)
          .build();
    }

    @SuppressWarnings("unchecked")
    private static RecordedResponseFormat extractResponseFormat(JsonNode outputConfig) {
      if (outputConfig.isMissingNode() || outputConfig.isNull()) {
        return null;
      }
      final var format = outputConfig.path("format");
      if (format.isMissingNode() || format.isNull()) {
        return null;
      }
      final var schema = OBJECT_MAPPER.convertValue(format.path("schema"), Map.class);
      return new RecordedResponseFormat(format.path("type").asText(), schema);
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
  }
}
