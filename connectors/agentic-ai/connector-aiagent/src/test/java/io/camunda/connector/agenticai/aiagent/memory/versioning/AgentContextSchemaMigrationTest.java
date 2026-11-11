/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.versioning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.MessageId;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AgentContextSchemaMigrationTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;

  @Nested
  class UpcastMessages {

    @Test
    void nonArrayNodeIsNoOp() throws Exception {
      JsonNode node = objectMapper.readTree("{\"not\": \"an array\"}");
      // no exception, no-op
      AgentContextSchemaMigration.ToolCallResultUpcaster.upcastMessages(node, objectMapper);
      assertThat(node.toString()).isEqualTo("{\"not\":\"an array\"}");
    }

    @Test
    void nullNodeIsNoOp() {
      AgentContextSchemaMigration.ToolCallResultUpcaster.upcastMessages(null, objectMapper);
      // no exception thrown
    }

    @Test
    void nonToolCallResultMessagesAreUntouched() throws Exception {
      JsonNode messages =
          objectMapper.readTree(
              """
              [
                {"role": "user", "content": [{"type": "text", "text": "hi"}]},
                {"role": "assistant", "content": [{"type": "text", "text": "hello"}]}
              ]
              """);

      String before = messages.toString();
      AgentContextSchemaMigration.ToolCallResultUpcaster.upcastMessages(messages, objectMapper);

      assertThat(messages.toString()).isEqualTo(before);
    }

    @Test
    void toolCallResultMessageWithFlatContentIsLifted() throws Exception {
      JsonNode messagesArray =
          objectMapper.readTree(
              """
              [
                {
                  "role": "tool_call_result",
                  "results": [
                    {"id": "call-1", "name": "search", "content": "Found 3 items"}
                  ]
                }
              ]
              """);

      AgentContextSchemaMigration.ToolCallResultUpcaster.upcastMessages(
          messagesArray, objectMapper);

      JsonNode content = messagesArray.get(0).get("results").get(0).get("content");
      assertThat(content.isArray()).isTrue();
      assertThat(content.get(0).get("type").asText()).isEqualTo("text");
      assertThat(content.get(0).get("text").asText()).isEqualTo("Found 3 items");
    }

    @Test
    void toolCallResultMessageWithMultiBlockLegacyContentStaysOpaque() throws Exception {
      // upcastMessages() itself is unconditional (version gating is the caller's job, see
      // MigrateAndBindAgentContext below) -- it lifts whatever content it finds, deterministically
      // wrapping a multi-element array as a single opaque block rather than splitting it
      JsonNode messagesArray =
          objectMapper.readTree(
              """
              [
                {
                  "role": "tool_call_result",
                  "results": [
                    {
                      "id": "call-1",
                      "name": "search",
                      "content": [{"type": "text", "text": "hi"}, {"type": "object", "content": {}}]
                    }
                  ]
                }
              ]
              """);

      AgentContextSchemaMigration.ToolCallResultUpcaster.upcastMessages(
          messagesArray, objectMapper);

      JsonNode content = messagesArray.get(0).get("results").get(0).get("content");
      assertThat(content.isArray()).isTrue();
      assertThat(content).hasSize(1);
      assertThat(content.get(0).get("type").asText()).isEqualTo("object");
    }

    @Test
    void resultsFieldOfNonArrayShapeIsNoOp() throws Exception {
      JsonNode messages =
          objectMapper.readTree(
              "[{\"role\": \"tool_call_result\", \"results\": {\"not\": \"an array\"}}]");
      String before = messages.toString();

      AgentContextSchemaMigration.ToolCallResultUpcaster.upcastMessages(messages, objectMapper);

      assertThat(messages.toString()).isEqualTo(before);
    }

    @Test
    void missingResultsFieldIsNoOp() throws Exception {
      JsonNode messages = objectMapper.readTree("[{\"role\": \"tool_call_result\"}]");
      String before = messages.toString();

      AgentContextSchemaMigration.ToolCallResultUpcaster.upcastMessages(messages, objectMapper);

      assertThat(messages.toString()).isEqualTo(before);
    }

    @Test
    void resultWithoutContentFieldIsUntouched() throws Exception {
      JsonNode messages =
          objectMapper.readTree(
              """
              [
                {
                  "role": "tool_call_result",
                  "results": [{"id": "call-1", "name": "search"}]
                }
              ]
              """);
      String before = messages.toString();

      AgentContextSchemaMigration.ToolCallResultUpcaster.upcastMessages(messages, objectMapper);

      assertThat(messages.toString()).isEqualTo(before);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void blankOrNullContentBecomesEmptyList(String text) throws Exception {
      JsonNode contentNode =
          text == null ? objectMapper.getNodeFactory().nullNode() : textNode(text);

      assertThat(liftedContentOf(contentNode)).isEmpty();
    }

    @Test
    void nonBlankTextualContentBecomesSingleTextBlock() throws Exception {
      JsonNode lifted = liftedContentOf(textNode("hello"));
      assertThat(lifted).hasSize(1);
      assertThat(lifted.get(0).get("type").asText()).isEqualTo("text");
      assertThat(lifted.get(0).get("text").asText()).isEqualTo("hello");
    }

    @Test
    void documentReferenceContentBecomesSingleDocumentBlock() throws Exception {
      // shaped like the real 8.9 golden fixtures' document objects (discriminator key +
      // storeId/documentId/contentHash/metadata)
      JsonNode contentNode =
          objectMapper.readTree(
              """
              {
                "camunda.document.type": "camunda",
                "storeId": "in-memory",
                "documentId": "31127ad5-411e-485a-a67b-f7b4512bc075",
                "contentHash": "37aab54a0d7d35291088a50ff9095845cdd292bc7b811008625cab10e75d2d0d",
                "metadata": {
                  "contentType": "application/json",
                  "fileName": "test.json"
                }
              }
              """);

      JsonNode lifted = liftedContentOf(contentNode);
      assertThat(lifted).hasSize(1);
      assertThat(lifted.get(0).get("type").asText()).isEqualTo("document");
    }

    @Test
    void plainObjectContentBecomesSingleObjectBlock() throws Exception {
      JsonNode contentNode = objectMapper.readTree("{\"key\": \"value\", \"count\": 3}");

      JsonNode lifted = liftedContentOf(contentNode);
      assertThat(lifted).hasSize(1);
      assertThat(lifted.get(0).get("type").asText()).isEqualTo("object");
      assertThat(lifted.get(0).get("content").get("key").asText()).isEqualTo("value");
    }

    @Test
    void arrayOfUntypedValuesBecomesSingleObjectBlockWrappingTheWholeArray() throws Exception {
      JsonNode contentNode =
          objectMapper.readTree(
              "[{\"id\": 1, \"name\": \"Alice\"}, {\"id\": 2, \"name\": \"Bob\"}]");

      JsonNode lifted = liftedContentOf(contentNode);
      assertThat(lifted).hasSize(1);
      assertThat(lifted.get(0).get("type").asText()).isEqualTo("object");
      assertThat(lifted.get(0).get("content")).hasSize(2);
    }

    @Test
    void arrayLookingLikeContentBlocksStaysOpaque() throws Exception {
      // the exact Copilot-flagged collision: a legacy gateway List<McpContent> whose elements
      // use the same type discriminators (text/object) as domain Content must NOT be split
      JsonNode contentNode =
          objectMapper.readTree(
              """
              [
                {"type": "text", "text": "hello"},
                {"type": "object", "content": {"key": "value"}}
              ]
              """);

      JsonNode lifted = liftedContentOf(contentNode);
      assertThat(lifted).hasSize(1);
      assertThat(lifted.get(0).get("type").asText()).isEqualTo("object");
      assertThat(lifted.get(0).get("content")).hasSize(2);
    }

    @Test
    void numberContentBecomesSingleObjectBlock() throws Exception {
      JsonNode lifted = liftedContentOf(objectMapper.getNodeFactory().numberNode(42));
      assertThat(lifted).hasSize(1);
      assertThat(lifted.get(0).get("content").asInt()).isEqualTo(42);
    }

    @Test
    void booleanContentBecomesSingleObjectBlock() throws Exception {
      JsonNode lifted = liftedContentOf(objectMapper.getNodeFactory().booleanNode(true));
      assertThat(lifted).hasSize(1);
      assertThat(lifted.get(0).get("content").asBoolean()).isTrue();
    }

    private JsonNode textNode(String text) {
      return objectMapper.getNodeFactory().textNode(text);
    }

    /** Lifts {@code contentNode} through the public {@code upcastMessages} entry point. */
    private JsonNode liftedContentOf(JsonNode contentNode) throws Exception {
      JsonNode messages =
          objectMapper.readTree(
              """
              [
                {
                  "role": "tool_call_result",
                  "results": [{"id": "call-1", "name": "search"}]
                }
              ]
              """);
      JsonNode result = messages.get(0).get("results").get(0);
      ((ObjectNode) result).set("content", contentNode);

      AgentContextSchemaMigration.ToolCallResultUpcaster.upcastMessages(messages, objectMapper);

      return result.get("content");
    }
  }

  @Nested
  class MigrateAndBindAgentContext {

    @Test
    void nullTreeReturnsNull() throws Exception {
      assertThat(AgentContextSchemaMigration.migrateAndBindAgentContext(null, objectMapper))
          .isNull();
    }

    @Test
    void nullNodeReturnsNull() throws Exception {
      JsonNode node = objectMapper.getNodeFactory().nullNode();
      assertThat(AgentContextSchemaMigration.migrateAndBindAgentContext(node, objectMapper))
          .isNull();
    }

    @Test
    void currentVersionInputIsNoOpForMigrationButStillBinds() throws Exception {
      JsonNode tree =
          objectMapper.readTree(
              """
              {
                "schemaVersion": 2,
                "state": "READY",
                "metrics": {"modelCalls": 1, "tokenUsage": {"inputTokenCount": 1, "outputTokenCount": 1}},
                "toolDefinitions": [],
                "conversation": {
                  "type": "in-process",
                  "conversationId": "test",
                  "messages": [
                    {
                      "role": "tool_call_result",
                      "results": [
                        {"id": "call-1", "name": "search", "content": [{"type": "text", "text": "hi"}]}
                      ]
                    }
                  ]
                },
                "properties": {}
              }
              """);

      AgentContext agentContext =
          AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);

      assertThat(agentContext).isNotNull();
      assertThat(agentContext.schemaVersion()).isEqualTo(2);
    }

    @Test
    void missingSchemaVersionIsTreatedAsLegacyAndUpcasted() throws Exception {
      JsonNode tree =
          objectMapper.readTree(
              """
              {
                "state": "READY",
                "metrics": {"modelCalls": 1, "tokenUsage": {"inputTokenCount": 1, "outputTokenCount": 1}},
                "toolDefinitions": [],
                "conversation": {
                  "type": "in-process",
                  "conversationId": "test",
                  "messages": [
                    {
                      "role": "tool_call_result",
                      "results": [
                        {"id": "call-1", "name": "search", "content": "Found 3 items"}
                      ]
                    }
                  ]
                },
                "properties": {}
              }
              """);

      AgentContext agentContext =
          AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);

      assertThat(agentContext).isNotNull();
      // the in-memory object is current-shape after upcast, even though it was read from
      // legacy-shaped JSON without a schemaVersion field
      assertThat(agentContext.schemaVersion()).isEqualTo(2);
    }

    @Test
    void explicitLowerSchemaVersionIsStampedToCurrent() throws Exception {
      JsonNode tree =
          objectMapper.readTree(
              """
              {
                "schemaVersion": 0,
                "state": "READY",
                "metrics": {"modelCalls": 1, "tokenUsage": {"inputTokenCount": 1, "outputTokenCount": 1}},
                "toolDefinitions": [],
                "conversation": {
                  "type": "in-process",
                  "conversationId": "test",
                  "messages": [
                    {
                      "role": "tool_call_result",
                      "results": [
                        {"id": "call-1", "name": "search", "content": "Found 3 items"}
                      ]
                    }
                  ]
                },
                "properties": {}
              }
              """);

      AgentContext agentContext =
          AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);

      assertThat(agentContext).isNotNull();
      // an explicit lower schemaVersion (not just a missing one) is also stamped to current, so
      // it isn't re-upcasted (and content re-wrapped) on the next read/write cycle
      assertThat(agentContext.schemaVersion()).isEqualTo(2);
    }

    @Test
    void reRunOnMigratedAndReserializedTreeIsIdempotent() throws Exception {
      JsonNode tree =
          objectMapper.readTree(
              """
              {
                "state": "READY",
                "metrics": {"modelCalls": 1, "tokenUsage": {"inputTokenCount": 1, "outputTokenCount": 1}},
                "toolDefinitions": [],
                "conversation": {
                  "type": "in-process",
                  "conversationId": "test",
                  "messages": [
                    {
                      "role": "tool_call_result",
                      "results": [
                        {"id": "call-1", "name": "search", "content": "Found 3 items"}
                      ]
                    }
                  ]
                },
                "properties": {}
              }
              """);

      AgentContext firstPass =
          AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);
      assertThat(firstPass).isNotNull();

      // simulate the write-then-read-again cycle: reserialize the migrated context back to a
      // tree (as it would be persisted), then migrate-and-bind it a second time
      JsonNode reserializedTree = objectMapper.valueToTree(firstPass);
      AgentContext secondPass =
          AgentContextSchemaMigration.migrateAndBindAgentContext(reserializedTree, objectMapper);

      assertThat(secondPass).isNotNull();
      assertThat(secondPass.schemaVersion()).isEqualTo(2);

      InProcessConversationContext conversation =
          (InProcessConversationContext) secondPass.conversation();
      assertThat(conversation).isNotNull();
      ToolCallResultMessage message = (ToolCallResultMessage) conversation.messages().get(0);
      // content stays a single TextContent -- it must not be re-wrapped into a nested/opaque
      // ObjectContent by a redundant second upcast pass
      assertThat(message.results().get(0).content())
          .containsExactly(TextContent.textContent("Found 3 items"));
      assertThat(secondPass).isEqualTo(firstPass);
    }

    @Test
    void unversionedStateIsMigratedAndBoundWithSchemaVersion2() throws Exception {
      JsonNode tree = objectMapper.readTree(stateJson(null, "\"Found 3 items\""));

      AgentContext agentContext =
          AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);

      assertThat(agentContext).isEqualTo(expectedAgentContext());
    }

    @Test
    void version1StateLoadsUnchangedAndIsBoundWithSchemaVersion2() throws Exception {
      JsonNode tree =
          objectMapper.readTree(
              stateJson(1, "[{\"type\": \"text\", \"text\": \"Found 3 items\"}]"));

      AgentContext agentContext =
          AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);

      assertThat(agentContext).isEqualTo(expectedAgentContext());
    }

    @Test
    void version2StateLoads() throws Exception {
      JsonNode tree =
          objectMapper.readTree(
              stateJson(2, "[{\"type\": \"text\", \"text\": \"Found 3 items\"}]"));

      AgentContext agentContext =
          AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);

      assertThat(agentContext).isEqualTo(expectedAgentContext());
    }

    @Test
    void version3StateIsRejected() throws Exception {
      JsonNode tree =
          objectMapper.readTree(
              stateJson(3, "[{\"type\": \"text\", \"text\": \"Found 3 items\"}]"));

      assertThatThrownBy(
              () -> AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(
              "Persisted conversation schema version 3 is newer than the highest version supported by this connector (2). This state was written by a newer connector version; rolling back to an older connector version is not supported. Upgrade the connector runtime to the version that wrote this state.");
    }

    @Test
    void versionStampOfAMigratedStateSurvivesTheNextReadWriteCycle() throws Exception {
      AgentContext migrated =
          AgentContextSchemaMigration.migrateAndBindAgentContext(
              objectMapper.readTree(stateJson(null, "\"Found 3 items\"")), objectMapper);

      JsonNode persisted = objectMapper.valueToTree(migrated);

      assertThat(persisted.get("schemaVersion").asInt()).isEqualTo(2);
      assertThat(AgentContextSchemaMigration.migrateAndBindAgentContext(persisted, objectMapper))
          .isEqualTo(expectedAgentContext());
    }

    private static String stateJson(Integer schemaVersion, String toolCallResultContent) {
      final var versionField =
          schemaVersion == null ? "" : "\"schemaVersion\": %d,".formatted(schemaVersion);
      return """
      {
        %s
        "state": "READY",
        "metrics": {"modelCalls": 1, "tokenUsage": {"inputTokenCount": 10, "outputTokenCount": 20}, "toolCalls": 1},
        "toolDefinitions": [],
        "conversation": {
          "type": "in-process",
          "conversationId": "test",
          "messages": [
            {
              "role": "assistant",
              "id": "00000000-0000-7000-8000-000000000001",
              "toolCalls": [{"id": "call-1", "name": "search", "arguments": {"q": "x"}}]
            },
            {
              "role": "tool_call_result",
              "id": "00000000-0000-7000-8000-000000000002",
              "results": [{"id": "call-1", "name": "search", "content": %s}]
            }
          ]
        },
        "properties": {}
      }
      """
          .formatted(versionField, toolCallResultContent);
    }

    private static AgentContext expectedAgentContext() {
      return new AgentContext(
          2,
          AgentState.READY,
          null,
          new AgentMetrics(
              1,
              AgentMetrics.TokenUsage.builder().inputTokenCount(10).outputTokenCount(20).build(),
              1),
          List.of(),
          new InProcessConversationContext(
              "test",
              List.of(
                  AssistantMessage.builder()
                      .id(MessageId.of("00000000-0000-7000-8000-000000000001"))
                      .toolCalls(
                          List.of(new ToolCall("call-1", "search", Map.of("q", "x"), Map.of())))
                      .build(),
                  ToolCallResultMessage.builder()
                      .id(MessageId.of("00000000-0000-7000-8000-000000000002"))
                      .results(
                          List.of(
                              ToolCallResultContent.builder()
                                  .id("call-1")
                                  .name("search")
                                  .content(List.of(TextContent.textContent("Found 3 items")))
                                  .build()))
                      .build())),
          AgentDocuments.empty(),
          List.of(),
          Map.of());
    }

    @Test
    void newerThanCurrentVersionThrows() throws Exception {
      JsonNode tree = objectMapper.readTree("{\"schemaVersion\": 3}");

      assertThatThrownBy(
              () -> AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(
              "Persisted conversation schema version 3 is newer than the highest version supported by this connector (2). This state was written by a newer connector version; rolling back to an older connector version is not supported. Upgrade the connector runtime to the version that wrote this state.");
    }

    @Test
    void toolCallResultSiblingFieldsAndMessageMetadataSurviveUpcasting() throws Exception {
      JsonNode tree =
          objectMapper.readTree(
              """
              {
                "state": "READY",
                "metrics": {"modelCalls": 1, "tokenUsage": {"inputTokenCount": 1, "outputTokenCount": 1}},
                "toolDefinitions": [],
                "conversation": {
                  "type": "in-process",
                  "conversationId": "test",
                  "messages": [
                    {
                      "role": "tool_call_result",
                      "metadata": {"timestamp": "2026-01-01T10:15:30Z"},
                      "results": [
                        {
                          "id": "call-1",
                          "name": "search",
                          "elementId": "Activity_1",
                          "completedAt": "2026-01-01T10:15:30Z",
                          "content": "Found 3 items",
                          "interrupted": true
                        }
                      ]
                    }
                  ]
                },
                "properties": {}
              }
              """);

      AgentContext agentContext =
          AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);

      assertThat(agentContext).isNotNull();
      InProcessConversationContext conversation =
          (InProcessConversationContext) agentContext.conversation();
      assertThat(conversation).isNotNull();
      ToolCallResultMessage message = (ToolCallResultMessage) conversation.messages().get(0);

      // message-level metadata survives
      assertThat(message.metadata()).containsEntry("timestamp", "2026-01-01T10:15:30Z");

      ToolCallResultContent result = message.results().get(0);
      // content was lifted to a single TextContent
      assertThat(result.content()).containsExactly(TextContent.textContent("Found 3 items"));
      // every sibling field of the lifted content survives untouched
      assertThat(result.id()).isEqualTo("call-1");
      assertThat(result.name()).isEqualTo("search");
      assertThat(result.elementId()).isEqualTo("Activity_1");
      assertThat(result.completedAt()).isEqualTo(OffsetDateTime.parse("2026-01-01T10:15:30Z"));
      assertThat(result.properties()).containsEntry("interrupted", true);
    }

    @Test
    void toolCallResultMessagesKeepTheirTypedBlocksThroughTheVersionedDeserialization()
        throws Exception {
      final var blocks =
          List.<Content>of(
              TextContent.textContent("Loaded the document."),
              new AgentDocumentContent(
                  AgentDocumentId.of("doc-1"), DocumentDelivery.REFERENCE, null),
              TextContent.textContent("Its content follows in the next message."));
      final var result =
          ToolCallResultContent.builder()
              .id("call-1")
              .name("search")
              .content(blocks)
              .completedAt(OffsetDateTime.parse("2026-10-05T10:15:30.123+02:00"))
              .build();
      final var context =
          AgentContext.builder()
              .conversation(
                  InProcessConversationContext.builder("conv")
                      .messages(
                          List.of(ToolCallResultMessage.builder().results(List.of(result)).build()))
                      .build())
              .build();

      final var serialized = objectMapper.writeValueAsString(context);
      final var bound = objectMapper.readValue(serialized, AgentContext.class);

      assertThat(bound.conversation())
          .isInstanceOfSatisfying(
              InProcessConversationContext.class,
              conversation ->
                  assertThat(conversation.messages())
                      .singleElement()
                      .isInstanceOfSatisfying(
                          ToolCallResultMessage.class,
                          message ->
                              assertThat(message.results().getFirst().content())
                                  .isEqualTo(blocks)));
    }

    @Test
    void pointerConversationWithoutMessagesIsUntouched() throws Exception {
      JsonNode tree =
          objectMapper.readTree(
              """
              {
                "state": "READY",
                "metrics": {"modelCalls": 1, "tokenUsage": {"inputTokenCount": 1, "outputTokenCount": 1}},
                "toolDefinitions": [],
                "conversation": {
                  "type": "aws-agentcore",
                  "conversationId": "test",
                  "memoryId": "m-1",
                  "actorId": "a-1"
                },
                "properties": {}
              }
              """);

      AgentContext agentContext =
          AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);

      assertThat(agentContext).isNotNull();
    }
  }
}
