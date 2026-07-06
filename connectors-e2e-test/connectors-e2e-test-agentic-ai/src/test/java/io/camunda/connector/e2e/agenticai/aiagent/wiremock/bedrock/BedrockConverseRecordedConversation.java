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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.bedrock;

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
 * Inspects the {@code POST /model/test-model/converse} requests the connector actually sent to
 * WireMock.
 *
 * <p>Bedrock's wire format is normalized the same way as Anthropic's so the shared {@code
 * ProviderWireFormatExpectedMessage} DSL can be reused unchanged: the system prompt is a top-level
 * {@code system} field, and tool results for a turn are batched into {@code toolResult} content
 * blocks of a single {@code user}-role message rather than sent as separate messages.
 *
 * <p>Unlike OpenAI/Anthropic, content blocks are discriminated by which key is present ({@code
 * text}, {@code toolUse}, {@code toolResult}) rather than a {@code type} field.
 */
public final class BedrockConverseRecordedConversation {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final List<RecordedChatRequest> requests;

  private BedrockConverseRecordedConversation(List<RecordedChatRequest> requests) {
    this.requests = requests;
  }

  /**
   * Reads and parses all recorded {@code POST /model/test-model/converse} requests, oldest first.
   */
  public static BedrockConverseRecordedConversation recorded() {
    final List<LoggedRequest> loggedRequests =
        new ArrayList<>(
            findAll(postRequestedFor(urlPathEqualTo(BedrockConverseChatModelStubs.CONVERSE_PATH))));

    loggedRequests.sort(Comparator.comparing(LoggedRequest::getLoggedDate));

    final List<RecordedChatRequest> parsed =
        loggedRequests.stream()
            .map(LoggedRequest::getBodyAsString)
            .map(RecordedChatRequest::parse)
            .toList();

    return new BedrockConverseRecordedConversation(parsed);
  }

  public List<RecordedChatRequest> requests() {
    return requests;
  }

  public record RecordedToolCall(String id, String name) {}

  /**
   * A content block, in Bedrock's own wire shape: {@code kind} is the single field name present on
   * the block (e.g. {@code text}, {@code image}, {@code document}) since Bedrock has no explicit
   * {@code type} discriminator field.
   */
  public record ContentBlock(String kind, String text) {}

  public record RecordedMessage(
      String role,
      List<ContentBlock> contentParts,
      List<RecordedToolCall> toolCalls,
      String toolCallId) {}

  public record RecordedResponseFormat(
      String type, String schemaName, Map<String, Object> jsonSchema) {}

  /** A single parsed Bedrock Converse request body. */
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
            StreamSupport.stream(root.path("toolConfig").path("tools").spliterator(), false)
                .map(tool -> tool.path("toolSpec"))
                .map(RecordedChatRequest::toToolDefinition)
                .toList();

        final var responseFormat = extractResponseFormat(root.path("outputConfig"));

        return new RecordedChatRequest(messages, tools, responseFormat);
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to parse recorded Bedrock Converse request body: " + rawBody, e);
      }
    }

    private static String extractSystemText(JsonNode system) {
      if (system.isMissingNode() || system.isNull()) {
        return null;
      }
      return StreamSupport.stream(system.spliterator(), false)
          .filter(block -> !block.path("text").isMissingNode())
          .map(block -> block.path("text").asText())
          .collect(Collectors.joining());
    }

    private static List<RecordedMessage> toRecordedMessages(JsonNode message) {
      final var role = message.path("role").asText();
      final var content = message.path("content");

      final boolean isToolResultMessage =
          StreamSupport.stream(content.spliterator(), false)
              .anyMatch(block -> !block.path("toolResult").isMissingNode());

      if ("user".equals(role) && isToolResultMessage) {
        final List<RecordedMessage> toolResults = new ArrayList<>();
        content.forEach(
            block -> {
              final var toolResult = block.path("toolResult");
              if (!toolResult.isMissingNode()) {
                toolResults.add(
                    new RecordedMessage(
                        "tool",
                        List.of(
                            new ContentBlock("text", toolResultText(toolResult.path("content")))),
                        List.of(),
                        toolResult.path("toolUseId").asText()));
              }
            });
        return toolResults;
      }

      // Content parts exclude toolUse blocks - those become toolCalls() instead.
      final var contentParts =
          StreamSupport.stream(content.spliterator(), false)
              .filter(block -> block.path("toolUse").isMissingNode())
              .map(
                  block -> {
                    final var kind = blockKind(block);
                    return new ContentBlock(
                        kind, "text".equals(kind) ? block.path("text").asText() : null);
                  })
              .toList();

      final var toolCalls =
          StreamSupport.stream(content.spliterator(), false)
              .filter(block -> !block.path("toolUse").isMissingNode())
              .map(block -> block.path("toolUse"))
              .map(
                  toolUse ->
                      new RecordedToolCall(
                          toolUse.path("toolUseId").asText(), toolUse.path("name").asText()))
              .toList();

      return List.of(new RecordedMessage(role, contentParts, toolCalls, null));
    }

    /** Bedrock content blocks have no {@code type} field; the single field present is the kind. */
    private static String blockKind(JsonNode block) {
      final var fieldNames = block.fieldNames();
      return fieldNames.hasNext() ? fieldNames.next() : "unknown";
    }

    private static String toolResultText(JsonNode content) {
      return StreamSupport.stream(content.spliterator(), false)
          .filter(block -> !block.path("text").isMissingNode())
          .map(block -> block.path("text").asText())
          .collect(Collectors.joining());
    }

    private static ToolDefinition toToolDefinition(JsonNode toolSpec) {
      final var inputSchema =
          OBJECT_MAPPER.convertValue(toolSpec.path("inputSchema").path("json"), Map.class);
      return ToolDefinition.builder()
          .name(toolSpec.path("name").asText())
          .description(toolSpec.path("description").asText())
          .inputSchema(inputSchema)
          .build();
    }

    @SuppressWarnings("unchecked")
    private static RecordedResponseFormat extractResponseFormat(JsonNode outputConfig) {
      if (outputConfig.isMissingNode() || outputConfig.isNull()) {
        return null;
      }
      final var textFormat = outputConfig.path("textFormat");
      if (textFormat.isMissingNode() || textFormat.isNull()) {
        return null;
      }
      final var jsonSchemaNode = textFormat.path("structure").path("jsonSchema");
      final var schemaJson = jsonSchemaNode.path("schema").asText();
      final Map<String, Object> schema;
      try {
        schema = OBJECT_MAPPER.readValue(schemaJson, Map.class);
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to parse Bedrock jsonSchema.schema: " + schemaJson, e);
      }
      return new RecordedResponseFormat(
          textFormat.path("type").asText(), jsonSchemaNode.path("name").asText(null), schema);
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
