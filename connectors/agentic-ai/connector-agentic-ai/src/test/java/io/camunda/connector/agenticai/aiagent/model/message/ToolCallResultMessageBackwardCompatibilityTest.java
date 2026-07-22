/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSchemaMigration;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext.DocumentContent;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationSerializer;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Backward-compatibility golden-fixture tests: real Camunda-8.9-persisted {@code agentContext}
 * conversations (staged, untouched, at {@code
 * src/test/resources/backwardcompatibility/camunda-8.9/}) must still deserialize after C4
 * introduces {@link io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent} as the
 * persisted tool-call-result element type — old flat {@code ToolCallResult}-shaped JSON must lift
 * losslessly into the new structured {@code List<Content>} shape.
 */
class ToolCallResultMessageBackwardCompatibilityTest {

  private static final String FIXTURE_BASE_PATH = "/backwardcompatibility/camunda-8.9/";

  // the connectors ObjectMapper, incl. the document module — see AgentContextTest for the
  // equivalent setup pattern (that test uses a plain ObjectMapper since it has no document
  // content; these fixtures do reference documents via the pointer conversation variant)
  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;

  @Test
  void inProcessFixtureDeserializesToolCallResultsAsStructuredContent() throws IOException {
    AgentContext agentContext = readAgentContextFixture("agentContext-inprocess.json");

    assertThat(agentContext.conversation()).isInstanceOf(InProcessConversationContext.class);
    var conversation = (InProcessConversationContext) agentContext.conversation();

    var toolCallResultMessages = toolCallResultMessages(conversation.messages());
    assertThat(toolCallResultMessages).hasSize(4);

    assertListUsersAndJokesApiMessage(
        toolCallResultMessages.get(0), "Who is Santa's favourite singer?\n\nElf-is Presley!");
    assertLoadUserByIdMessage(toolCallResultMessages.get(1));
    assertAskHumanToSendEmailMessage(toolCallResultMessages.get(2));
    assertAskHumanToSendEmailMessage(toolCallResultMessages.get(3));

    // round-trip guard: the new format is self-consistent
    String reserialized = objectMapper.writeValueAsString(agentContext);
    AgentContext roundTripped = objectMapper.readValue(reserialized, AgentContext.class);
    assertThat(roundTripped).isEqualTo(agentContext);
  }

  @Test
  void documentPayloadFixtureDeserializesToolCallResultsAsStructuredContent() throws IOException {
    var documentContent = readDocumentContentFixture("conversation-document-payload.json");

    var toolCallResultMessages = toolCallResultMessages(documentContent.messages());
    assertThat(toolCallResultMessages).hasSize(4);

    // same structural shapes as the in-process fixture; the payload text differs (a different
    // recorded LLM run), so only the content type is pinned here, not the literal joke text
    var listUsersAndJokes = toolCallResultMessages.get(0);
    assertThat(listUsersAndJokes.results()).hasSize(2);
    assertListUsersResult(listUsersAndJokes.results().get(0));
    var jokesApi = listUsersAndJokes.results().get(1);
    assertThat(jokesApi.name()).isEqualTo("Jokes_API");
    assertThat(jokesApi.content()).singleElement().isInstanceOf(TextContent.class);

    assertLoadUserByIdMessage(toolCallResultMessages.get(1));
    assertAskHumanToSendEmailMessage(toolCallResultMessages.get(2));
    assertAskHumanToSendEmailMessage(toolCallResultMessages.get(3));

    // round-trip guard
    String reserialized = objectMapper.writeValueAsString(documentContent);
    var roundTripped =
        objectMapper.readValue(
            reserialized, CamundaDocumentConversationContext.DocumentContent.class);
    assertThat(roundTripped).isEqualTo(documentContent);
  }

  @Test
  void camundaDocumentPointerFixtureDeserializesToCamundaDocumentConversationContext()
      throws IOException {
    AgentContext agentContext = readAgentContextFixture("agentContext-camunda-document.json");

    assertThat(agentContext.conversation()).isInstanceOf(CamundaDocumentConversationContext.class);
  }

  /**
   * Regression pin for the exact collision Copilot flagged in review: an 8.9 tool-call-result whose
   * {@code content} is a multi-block array that happens to look like a domain {@code List<Content>}
   * (each element uses one of {@code Content}'s own type discriminators — text/object/document —
   * and, incidentally, the same field names too). Under the removed shape-inference heuristic this
   * would mis-split into several typed {@link
   * io.camunda.connector.agenticai.aiagent.model.message.content.Content} blocks. The
   * schema-version-gated migration must instead treat it as legacy data regardless of how its
   * elements look and lift the *whole* array into a single opaque {@link ObjectContent} — exactly
   * as it would for e.g. a persisted gateway (MCP/A2A) {@code List<McpContent>} result, whose
   * elements share the very same type discriminator names.
   */
  @Test
  void multiBlockLegacyContentThatLooksLikeContentBlocksStaysOpaque() throws IOException {
    String json =
        """
        {
          "state": "READY",
          "metrics": {"modelCalls": 1, "tokenUsage": {"inputTokenCount": 1, "outputTokenCount": 1}},
          "toolDefinitions": [],
          "conversation": {
            "type": "in-process",
            "conversationId": "collision-test",
            "messages": [
              {
                "role": "tool_call_result",
                "results": [
                  {
                    "id": "call-1",
                    "name": "gatewayTool",
                    "content": [
                      {"type": "text", "text": "hello"},
                      {"type": "object", "content": {"key": "value"}}
                    ]
                  }
                ]
              }
            ]
          },
          "properties": {}
        }
        """;

    AgentContext agentContext =
        ConversationSchemaMigration.migrateAndBindAgentContext(
            objectMapper.readTree(json), objectMapper);

    assertThat(agentContext.conversation()).isInstanceOf(InProcessConversationContext.class);
    var conversation = (InProcessConversationContext) agentContext.conversation();
    var toolCallResultMessages = toolCallResultMessages(conversation.messages());
    assertThat(toolCallResultMessages).hasSize(1);

    var result = toolCallResultMessages.get(0).results().getFirst();
    assertThat(result.content())
        .singleElement()
        .isInstanceOfSatisfying(
            ObjectContent.class,
            objectContent -> assertThat((List<?>) objectContent.content()).hasSize(2));
  }

  private void assertListUsersAndJokesApiMessage(
      ToolCallResultMessage message, String expectedJokeText) {
    assertThat(message.results()).hasSize(2);
    assertListUsersResult(message.results().get(0));

    var jokesApi = message.results().get(1);
    assertThat(jokesApi.name()).isEqualTo("Jokes_API");
    assertThat(jokesApi.elementId()).isNull();
    assertThat(jokesApi.completedAt()).isNull();
    assertThat(jokesApi.content()).containsExactly(TextContent.textContent(expectedJokeText));
  }

  private void assertListUsersResult(ToolCallResultContent listUsers) {
    assertThat(listUsers.id()).isNotBlank();
    assertThat(listUsers.name()).isEqualTo("ListUsers");
    assertThat(listUsers.elementId()).isNull();
    assertThat(listUsers.completedAt()).isNull();
    assertThat(listUsers.content())
        .singleElement()
        .isInstanceOfSatisfying(
            ObjectContent.class,
            objectContent -> {
              assertThat(objectContent.content()).isInstanceOf(List.class);
              assertThat((List<?>) objectContent.content()).hasSize(10);
            });
  }

  private void assertLoadUserByIdMessage(ToolCallResultMessage message) {
    assertThat(message.results()).hasSize(1);
    var loadUserByID = message.results().getFirst();
    assertThat(loadUserByID.id()).isNotBlank();
    assertThat(loadUserByID.name()).isEqualTo("LoadUserByID");
    assertThat(loadUserByID.elementId()).isNull();
    assertThat(loadUserByID.completedAt()).isNull();
    assertThat(loadUserByID.content())
        .singleElement()
        .isInstanceOfSatisfying(
            ObjectContent.class,
            objectContent -> assertThat(objectContent.content()).isInstanceOf(Map.class));
  }

  private void assertAskHumanToSendEmailMessage(ToolCallResultMessage message) {
    assertThat(message.results()).hasSize(1);
    var askHumanToSendEmail = message.results().getFirst();
    assertThat(askHumanToSendEmail.id()).isNotBlank();
    assertThat(askHumanToSendEmail.name()).isEqualTo("AskHumanToSendEmail");
    assertThat(askHumanToSendEmail.elementId()).isNull();
    assertThat(askHumanToSendEmail.completedAt()).isNull();
    assertThat(askHumanToSendEmail.content())
        .singleElement()
        .isInstanceOfSatisfying(
            ObjectContent.class,
            objectContent -> assertThat(objectContent.content()).isInstanceOf(Map.class));
  }

  private List<ToolCallResultMessage> toolCallResultMessages(List<Message> messages) {
    return messages.stream()
        .filter(ToolCallResultMessage.class::isInstance)
        .map(ToolCallResultMessage.class::cast)
        .toList();
  }

  /**
   * Reads a persisted {@code agentContext} golden fixture through the same migrate-on-read path
   * production code uses ({@code VersionedAgentContextDeserializer}): these 8.9 fixtures predate
   * {@code schemaVersion}, so a direct {@code readValue} would no longer upcast legacy tool-call
   * results by design (see {@link ToolCallResultContent}) — it must go through {@link
   * ConversationSchemaMigration#migrateAndBindAgentContext}.
   */
  private AgentContext readAgentContextFixture(String fileName) throws IOException {
    return ConversationSchemaMigration.migrateAndBindAgentContext(
        readFixtureTree(fileName), objectMapper);
  }

  /**
   * Reads a persisted conversation-document-store payload fixture through the real production
   * {@link CamundaDocumentConversationSerializer#readDocumentContent}: this fixture predates {@code
   * schemaVersion} (it is a legacy Camunda 8.9 payload with no such field), so the version gate
   * inside that method must detect absent -&gt; legacy and upcast its {@code messages} before
   * binding.
   */
  private DocumentContent readDocumentContentFixture(String fileName) throws IOException {
    final var serializer = new CamundaDocumentConversationSerializer(objectMapper);
    final var document = Mockito.mock(io.camunda.connector.api.document.Document.class);
    Mockito.when(document.asInputStream())
        .thenReturn(getClass().getResourceAsStream(FIXTURE_BASE_PATH + fileName));
    return serializer.readDocumentContent(document);
  }

  private JsonNode readFixtureTree(String fileName) throws IOException {
    try (InputStream stream = getClass().getResourceAsStream(FIXTURE_BASE_PATH + fileName)) {
      assertThat(stream).as("fixture resource %s", fileName).isNotNull();
      return objectMapper.readTree(stream);
    }
  }
}
