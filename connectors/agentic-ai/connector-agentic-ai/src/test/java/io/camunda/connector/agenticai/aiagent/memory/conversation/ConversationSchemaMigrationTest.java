/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ConversationSchemaMigrationTest {

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;

  @Nested
  class LiftLegacyContent {

    @Test
    void nullNodeBecomesEmptyList() {
      assertThat(ConversationSchemaMigration.liftLegacyContent(null, objectMapper)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void blankTextualNodeBecomesEmptyList(String text) {
      JsonNode node = text == null ? objectMapper.getNodeFactory().nullNode() : textNode(text);
      assertThat(ConversationSchemaMigration.liftLegacyContent(node, objectMapper)).isEmpty();
    }

    @Test
    void nonBlankTextualNodeBecomesSingleTextContent() {
      JsonNode node = textNode("hello");
      assertThat(ConversationSchemaMigration.liftLegacyContent(node, objectMapper))
          .containsExactly(TextContent.textContent("hello"));
    }

    @Test
    void documentReferenceNodeBecomesSingleDocumentContent() throws Exception {
      // shaped like the real 8.9 golden fixtures' document objects (discriminator key +
      // storeId/documentId/contentHash/metadata)
      JsonNode node =
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

      assertThat(ConversationSchemaMigration.liftLegacyContent(node, objectMapper))
          .singleElement()
          .isInstanceOf(DocumentContent.class);
    }

    @Test
    void plainObjectNodeBecomesSingleObjectContent() throws Exception {
      JsonNode node = objectMapper.readTree("{\"key\": \"value\", \"count\": 3}");

      assertThat(ConversationSchemaMigration.liftLegacyContent(node, objectMapper))
          .singleElement()
          .isInstanceOfSatisfying(
              ObjectContent.class,
              objectContent -> assertThat(objectContent.content()).isInstanceOf(Map.class));
    }

    @Test
    void arrayOfUntypedValuesBecomesSingleObjectContentWrappingTheWholeArray() throws Exception {
      JsonNode node =
          objectMapper.readTree(
              "[{\"id\": 1, \"name\": \"Alice\"}, {\"id\": 2, \"name\": \"Bob\"}]");

      assertThat(ConversationSchemaMigration.liftLegacyContent(node, objectMapper))
          .singleElement()
          .isInstanceOfSatisfying(
              ObjectContent.class,
              objectContent -> assertThat((List<?>) objectContent.content()).hasSize(2));
    }

    @Test
    void arrayLookingLikeContentBlocksStaysOpaque() throws Exception {
      // the exact Copilot-flagged collision: a legacy gateway List<McpContent> whose elements
      // use the same type discriminators (text/object) as domain Content must NOT be split
      JsonNode node =
          objectMapper.readTree(
              """
              [
                {"type": "text", "text": "hello"},
                {"type": "object", "content": {"key": "value"}}
              ]
              """);

      assertThat(ConversationSchemaMigration.liftLegacyContent(node, objectMapper))
          .singleElement()
          .isInstanceOfSatisfying(
              ObjectContent.class,
              objectContent -> assertThat((List<?>) objectContent.content()).hasSize(2));
    }

    @Test
    void numberNodeBecomesSingleObjectContent() {
      JsonNode node = objectMapper.getNodeFactory().numberNode(42);

      assertThat(ConversationSchemaMigration.liftLegacyContent(node, objectMapper))
          .containsExactly(ObjectContent.objectContent(42));
    }

    @Test
    void booleanNodeBecomesSingleObjectContent() {
      JsonNode node = objectMapper.getNodeFactory().booleanNode(true);

      assertThat(ConversationSchemaMigration.liftLegacyContent(node, objectMapper))
          .containsExactly(ObjectContent.objectContent(true));
    }

    private JsonNode textNode(String text) {
      return objectMapper.getNodeFactory().textNode(text);
    }
  }

  @Nested
  class UpcastMessages {

    @Test
    void nonArrayNodeIsNoOp() throws Exception {
      JsonNode node = objectMapper.readTree("{\"not\": \"an array\"}");
      // no exception, no-op
      ConversationSchemaMigration.upcastMessages(node, objectMapper);
      assertThat(node.toString()).isEqualTo("{\"not\":\"an array\"}");
    }

    @Test
    void nullNodeIsNoOp() {
      ConversationSchemaMigration.upcastMessages(null, objectMapper);
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
      ConversationSchemaMigration.upcastMessages(messages, objectMapper);

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

      ConversationSchemaMigration.upcastMessages(messagesArray, objectMapper);

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

      ConversationSchemaMigration.upcastMessages(messagesArray, objectMapper);

      JsonNode content = messagesArray.get(0).get("results").get(0).get("content");
      assertThat(content.isArray()).isTrue();
      assertThat(content).hasSize(1);
      assertThat(content.get(0).get("type").asText()).isEqualTo("object");
    }
  }

  @Nested
  class UpcastToolCallResults {

    @Test
    void nonArrayNodeIsNoOp() throws Exception {
      JsonNode node = objectMapper.readTree("{\"not\": \"an array\"}");
      ConversationSchemaMigration.upcastToolCallResults(node, objectMapper);
      assertThat(node.toString()).isEqualTo("{\"not\":\"an array\"}");
    }

    @Test
    void nullNodeIsNoOp() {
      ConversationSchemaMigration.upcastToolCallResults(null, objectMapper);
    }

    @Test
    void resultsWithoutContentFieldAreUntouched() throws Exception {
      JsonNode results = objectMapper.readTree("[{\"id\": \"call-1\", \"name\": \"search\"}]");
      String before = results.toString();

      ConversationSchemaMigration.upcastToolCallResults(results, objectMapper);

      assertThat(results.toString()).isEqualTo(before);
    }

    @Test
    void flatContentIsLiftedInPlace() throws Exception {
      JsonNode results =
          objectMapper.readTree(
              "[{\"id\": \"call-1\", \"name\": \"search\", \"content\": \"Found 3 items\"}]");

      ConversationSchemaMigration.upcastToolCallResults(results, objectMapper);

      JsonNode content = results.get(0).get("content");
      assertThat(content.isArray()).isTrue();
      assertThat(content.get(0).get("type").asText()).isEqualTo("text");
    }
  }

  @Nested
  class MigrateAndBindAgentContext {

    @Test
    void nullTreeReturnsNull() throws Exception {
      assertThat(ConversationSchemaMigration.migrateAndBindAgentContext(null, objectMapper))
          .isNull();
    }

    @Test
    void nullNodeReturnsNull() throws Exception {
      JsonNode node = objectMapper.getNodeFactory().nullNode();
      assertThat(ConversationSchemaMigration.migrateAndBindAgentContext(node, objectMapper))
          .isNull();
    }

    @Test
    void currentVersionInputIsNoOpForMigrationButStillBinds() throws Exception {
      JsonNode tree =
          objectMapper.readTree(
              """
              {
                "schemaVersion": %d,
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
              """
                  .formatted(AgentContext.CURRENT_SCHEMA_VERSION));

      AgentContext agentContext =
          ConversationSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);

      assertThat(agentContext).isNotNull();
      assertThat(agentContext.schemaVersion()).isEqualTo(AgentContext.CURRENT_SCHEMA_VERSION);
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
          ConversationSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);

      assertThat(agentContext).isNotNull();
      // the in-memory object is current-shape after upcast, even though it was read from
      // legacy-shaped JSON without a schemaVersion field
      assertThat(agentContext.schemaVersion()).isEqualTo(AgentContext.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void newerThanCurrentVersionThrows() throws Exception {
      final int futureVersion = AgentContext.CURRENT_SCHEMA_VERSION + 1;
      JsonNode tree = objectMapper.readTree("{\"schemaVersion\": %d}".formatted(futureVersion));

      assertThatThrownBy(
              () -> ConversationSchemaMigration.migrateAndBindAgentContext(tree, objectMapper))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(String.valueOf(futureVersion))
          .hasMessageContaining("newer")
          .hasMessageContaining("not supported");
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
          ConversationSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);

      assertThat(agentContext).isNotNull();
    }
  }
}
