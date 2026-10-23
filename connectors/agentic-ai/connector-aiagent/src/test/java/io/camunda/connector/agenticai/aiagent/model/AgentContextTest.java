/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AgentContextTest {
  private static final AgentContext EMPTY_CONTEXT = AgentContext.empty();

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void emptyContext() {
    final var context = AgentContext.empty();
    assertThat(context.state()).isEqualTo(AgentState.INITIALIZING);
    assertThat(context.metrics()).isEqualTo(AgentMetrics.empty());
    assertThat(context.toolDefinitions()).isEmpty();
    assertThat(context.conversation()).isNull();
    assertThat(context).isNotSameAs(EMPTY_CONTEXT).isEqualTo(EMPTY_CONTEXT);
  }

  @Test
  void schemaVersionDefaultsToCurrentAndSerializesAsFirstField() throws JsonProcessingException {
    final var context = AgentContext.empty();
    assertThat(context.schemaVersion()).isEqualTo(AgentContext.CURRENT_SCHEMA_VERSION);

    final var serialized = objectMapper.writeValueAsString(context);
    assertThat(serialized).startsWith("{\"schemaVersion\":" + AgentContext.CURRENT_SCHEMA_VERSION);

    final var deserialized = objectMapper.readValue(serialized, AgentContext.class);
    assertThat(deserialized.schemaVersion()).isEqualTo(AgentContext.CURRENT_SCHEMA_VERSION);
  }

  @Test
  void agentInstanceKeyIsReadFromTheMetadata() {
    final var metadata = new AgentMetadata(1L, 2L, AgentInstanceKey.of(42L), 3);

    assertThat(AgentContext.empty().agentInstanceKey()).isNull();
    assertThat(
            AgentContext.empty()
                .withMetadata(new AgentMetadata(1L, 2L, null, 3))
                .agentInstanceKey())
        .isNull();
    assertThat(AgentContext.empty().withMetadata(metadata).agentInstanceKey())
        .isEqualTo(AgentInstanceKey.of(42L));
  }

  @Test
  void agentInstanceKeyIsNotSerializedOutsideTheMetadata() throws JsonProcessingException {
    final var context =
        AgentContext.empty().withMetadata(new AgentMetadata(1L, 2L, AgentInstanceKey.of(42L), 3));

    final var tree = objectMapper.readTree(objectMapper.writeValueAsString(context));

    assertThat(tree.has("agentInstanceKey")).isFalse();
    assertThat(tree.path("metadata").path("agentInstanceKey").asLong()).isEqualTo(42L);
  }

  @Test
  void withState() {
    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withState(AgentState.TOOL_DISCOVERY);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.state()).isEqualTo(EMPTY_CONTEXT.state());

    assertThat(updatedContext.state()).isEqualTo(AgentState.TOOL_DISCOVERY);
  }

  @Test
  void withMetrics() {
    final var updatedMetrics = new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0);

    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withMetrics(updatedMetrics);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.metrics()).isEqualTo(EMPTY_CONTEXT.metrics());

    assertThat(updatedContext.metrics())
        .isEqualTo(updatedMetrics)
        .isNotEqualTo(initialContext.metrics());
  }

  @Test
  void withToolDefinitions() {
    final var toolDefinition = ToolDefinition.builder().name("toolA").description("A tool").build();
    final var toolDefinitions = List.of(toolDefinition);

    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withToolDefinitions(toolDefinitions);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.toolDefinitions()).isEqualTo(EMPTY_CONTEXT.toolDefinitions());

    assertThat(updatedContext.toolDefinitions())
        .isNotNull()
        .containsExactlyElementsOf(toolDefinitions);
  }

  @Test
  void withConversation() {
    final var newMessage = userMessage("Hello");
    final var newConversationContext =
        InProcessConversationContext.builder("test-conversation")
            .messages(List.of(newMessage))
            .build();

    final var initialContext = AgentContext.empty();
    final var updatedContext = initialContext.withConversation(newConversationContext);

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(initialContext.conversation()).isEqualTo(EMPTY_CONTEXT.conversation());

    assertThat(updatedContext.conversation())
        .isNotNull()
        .isEqualTo(newConversationContext)
        .isNotEqualTo(initialContext.conversation());

    assertThat(((InProcessConversationContext) updatedContext.conversation()).messages())
        .containsExactly(newMessage);
  }

  @Test
  void withPropertyAddsToExistingProperties() {
    final var initialContext = AgentContext.builder().properties(Map.of("foo", "bar")).build();
    final var updatedContext = initialContext.withProperty("baz", "qux");

    assertThat(updatedContext).isNotEqualTo(initialContext);
    assertThat(updatedContext.properties())
        .containsExactlyInAnyOrderEntriesOf(Map.of("foo", "bar", "baz", "qux"));
  }

  @Test
  void documentsDefaultToEmptyAndAreOmittedFromJsonWhenEmpty() throws JsonProcessingException {
    final var context = AgentContext.empty();
    assertThat(context.documents().all()).isEmpty();

    final var serialized = objectMapper.writeValueAsString(context);
    assertThat(serialized).doesNotContain("documents");
  }

  @Test
  void agentContextWithoutDocumentsFieldBindsToEmptyDocuments() throws JsonProcessingException {
    final var json =
        """
        {
          "schemaVersion": 1,
          "state": "READY",
          "metrics": {"modelCalls": 1, "tokenUsage": {"inputTokenCount": 1, "outputTokenCount": 2}},
          "toolDefinitions": [],
          "conversation": {"type": "in-process", "conversationId": "c", "messages": []}
        }
        """;

    final var context = objectMapper.readValue(json, AgentContext.class);

    assertThat(context.documents().all()).isEmpty();
    assertThat(context.state()).isEqualTo(AgentState.READY);
    assertThat(context.schemaVersion()).isEqualTo(AgentContext.CURRENT_SCHEMA_VERSION);
  }

  @Test
  void documentsAreSerializedAndDeserialized() throws JsonProcessingException {
    final var entry =
        new AgentDocument(
            AgentDocumentId.of("doc-1"),
            DocumentOrigin.toolCall("search", "call-1"),
            new CamundaDocumentReferenceModel("in-memory", "doc-1", null, null));
    final var context = AgentContext.builder().documents(AgentDocuments.of(List.of(entry))).build();

    final var serialized = objectMapper.writeValueAsString(context);
    final var deserialized = objectMapper.readValue(serialized, AgentContext.class);

    assertThat(serialized).contains("\"documents\"");
    assertThat(deserialized.documents().all()).containsExactly(entry);
    assertThat(deserialized).isEqualTo(context);
  }

  @Test
  void documentsArePersistedAsAListOfIdOriginAndReference() throws JsonProcessingException {
    final var context =
        AgentContext.builder()
            .documents(
                AgentDocuments.of(
                    List.of(
                        new AgentDocument(
                            AgentDocumentId.of("doc_6d97d0f7b602"),
                            DocumentOrigin.toolCall("search", "call-1"),
                            new CamundaDocumentReferenceModel(
                                "in-memory",
                                "doc-1",
                                "hash-1",
                                new CamundaDocumentMetadataModel(
                                    "application/pdf",
                                    OffsetDateTime.parse("2030-01-02T03:04:05Z"),
                                    48213L,
                                    "report.pdf",
                                    null,
                                    null,
                                    null))),
                        new AgentDocument(
                            AgentDocumentId.of("doc_54f786b4cd7b"),
                            DocumentOrigin.event(),
                            new ExternalDocumentReferenceModel(
                                "https://example.com/a.pdf?token=secret", "a.pdf")))))
            .build();

    final var json =
        TestObjectMapperSupplier.INSTANCE
            .readTree(TestObjectMapperSupplier.INSTANCE.writeValueAsString(context))
            .get("documents");

    assertThat(json)
        .isEqualTo(
            TestObjectMapperSupplier.INSTANCE.readTree(
                """
                [{"id":"doc_6d97d0f7b602",
                  "origin":{"type":"TOOL_CALL","toolName":"search","toolCallId":"call-1"},
                  "reference":{"camunda.document.type":"camunda","storeId":"in-memory",
                    "documentId":"doc-1","contentHash":"hash-1",
                    "metadata":{"contentType":"application/pdf","expiresAt":"2030-01-02T03:04:05Z",
                      "size":48213,"fileName":"report.pdf"}}},
                 {"id":"doc_54f786b4cd7b","origin":{"type":"EVENT"},
                  "reference":{"camunda.document.type":"external",
                    "url":"https://example.com/a.pdf?token=secret","name":"a.pdf"}}]\
                """));
    assertThat(
            TestObjectMapperSupplier.INSTANCE.readValue(
                TestObjectMapperSupplier.INSTANCE.writeValueAsString(context), AgentContext.class))
        .isEqualTo(context);
  }

  @Test
  void documentsAreReadFromAndWrittenToTheDocumentsOfTheContext() {
    final var entry =
        new AgentDocument(
            AgentDocumentId.of("doc-1"),
            DocumentOrigin.userMessage(),
            new CamundaDocumentReferenceModel("in-memory", "doc-1", null, null));

    final var context = AgentContext.empty().withDocuments(AgentDocuments.of(List.of(entry)));

    assertThat(context.documents().all()).containsExactly(entry);
    assertThat(context.documents().find(AgentDocumentId.of("doc-1"))).contains(entry);
    assertThat(AgentContext.empty().documents().all()).isEmpty();
  }

  @Test
  void anAgentInstanceWithTheDocumentsFeatureTracksItsDocuments() {
    final var documents =
        AgentDocuments.of(
            List.of(
                new AgentDocument(
                    AgentDocumentId.of("doc-1"),
                    DocumentOrigin.userMessage(),
                    new CamundaDocumentReferenceModel("in-memory", "doc-1", null, null))));
    final var tracking =
        AgentContext.builder()
            .features(List.of(AgentFeatures.DOCUMENTS))
            .documents(documents)
            .build();

    assertThat(tracking.documentHandling()).isEqualTo(new DocumentHandling.Tracked(documents));
    assertThat(AgentContext.empty().documentHandling()).isEqualTo(new DocumentHandling.Untracked());
    assertThat(AgentContext.empty().withFeatures(AgentFeatures.ofNewInstance()).documentHandling())
        .isEqualTo(new DocumentHandling.Untracked());
  }

  @Test
  void theDocumentHandlingIsNotPersisted() throws JsonProcessingException {
    final var serialized =
        objectMapper.writeValueAsString(
            AgentContext.builder().features(List.of(AgentFeatures.DOCUMENTS)).build());

    assertThat(serialized).doesNotContain("documentHandling").doesNotContain("Tracked");
  }

  @Test
  void featuresDefaultToEmptyAndAreOmittedFromJsonWhenEmpty() throws JsonProcessingException {
    final var context = AgentContext.empty();

    assertThat(context.features()).isEmpty();
    assertThat(context.supports(AgentFeatures.RUNTIME_TOOLS)).isFalse();
    assertThat(objectMapper.writeValueAsString(context)).doesNotContain("features");
  }

  @Test
  void agentContextWithoutFeaturesFieldBindsToEmptySetAndKeepsTheSchemaVersion()
      throws JsonProcessingException {
    final var json =
        """
        {
          "schemaVersion": 1,
          "state": "READY",
          "metrics": {"modelCalls": 1, "tokenUsage": {"inputTokenCount": 1, "outputTokenCount": 2}},
          "toolDefinitions": []
        }
        """;

    final var context = objectMapper.readValue(json, AgentContext.class);

    assertThat(context.features()).isEmpty();
    assertThat(context.supports(AgentFeatures.RUNTIME_TOOLS)).isFalse();
    assertThat(context.schemaVersion()).isEqualTo(AgentContext.CURRENT_SCHEMA_VERSION);
    assertThat(objectMapper.writeValueAsString(context)).doesNotContain("features");
  }

  @Test
  void newInstanceFeaturesContainTheRuntimeToolsAndTheDocumentsFeature() {
    final var context = AgentContext.empty().withFeatures(AgentFeatures.ofNewInstance());

    assertThat(context.features()).containsExactly("runtimeTools", "documents");
    assertThat(context.supports(AgentFeatures.RUNTIME_TOOLS)).isTrue();
    assertThat(context.supports(AgentFeatures.DOCUMENTS)).isTrue();
    assertThat(context.supports("other")).isFalse();
  }

  @Test
  void featuresAreSerializedAndDeserializedInInsertionOrder() throws JsonProcessingException {
    final var features = List.of("zeta", "alpha", "runtimeTools", "beta", "omega", "gamma");
    final var context = AgentContext.builder().features(features).build();

    final var serialized = objectMapper.writeValueAsString(context);
    final var deserialized = objectMapper.readValue(serialized, AgentContext.class);

    assertThat(serialized)
        .contains(
            "\"features\":[\"zeta\",\"alpha\",\"runtimeTools\",\"beta\",\"omega\",\"gamma\"]");
    assertThat(deserialized.features())
        .containsExactly("zeta", "alpha", "runtimeTools", "beta", "omega", "gamma");
    assertThat(deserialized).isEqualTo(context);
  }

  @Test
  void featuresSurviveCopiesThatChangeOtherComponents() {
    final var features = List.of("zeta", "alpha", "runtimeTools", "beta", "omega", "gamma");
    final var context = AgentContext.builder().features(features).build();

    final var copy =
        context
            .withState(AgentState.READY)
            .withProperty("hello", "world")
            .withMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0));

    assertThat(copy.features())
        .containsExactly("zeta", "alpha", "runtimeTools", "beta", "omega", "gamma");
    assertThat(copy.supports(AgentFeatures.RUNTIME_TOOLS)).isTrue();
  }

  @Test
  void featuresAreCopiedDefensively() {
    final var features = new ArrayList<>(List.of("runtimeTools"));
    final var context = AgentContext.builder().features(features).build();

    features.add("other");

    assertThat(context.features()).containsExactly("runtimeTools");
  }

  @ParameterizedTest
  @MethodSource("invalidConstructorParameters")
  void throwsExceptionOnInvalidConstructorParameters(
      AgentState state,
      AgentMetrics metrics,
      List<ToolDefinition> toolDefinitions,
      String exceptionMessage) {
    assertThatThrownBy(() ->
            new AgentContext(
                AgentContext.CURRENT_SCHEMA_VERSION,
                state,
                null,
                metrics,
                toolDefinitions,
                InProcessConversationContext.builder("test-conversation").build(),
                AgentDocuments.empty(),
                List.of(),
                Map.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(exceptionMessage);
  }

  @Test
  void rejectsARuntimeToolDefinition() {
    final var runtime = ToolDefinition.runtime("acme_lookup", null, Map.of());

    assertThatThrownBy(() -> AgentContext.empty().withToolDefinitions(List.of(runtime)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("acme_lookup");
  }

  @Test
  void canBeSerializedAndDeserialized() throws JsonProcessingException {
    final var agentContext =
        AgentContext.builder()
            .metrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0))
            .toolDefinitions(
                List.of(
                    ToolDefinition.builder()
                        .name("toolA")
                        .description("A tool")
                        .inputSchema(Map.of("type", "object"))
                        .build()))
            .conversation(
                InProcessConversationContext.builder("test-conversation")
                    .messages(List.of(userMessage("Hello")))
                    .build())
            .build();

    final var serialized =
        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(agentContext);

    final var deserialized = objectMapper.readValue(serialized, AgentContext.class);

    assertThat(deserialized).usingRecursiveComparison().isEqualTo(agentContext);
  }

  static Stream<Arguments> invalidConstructorParameters() {
    final var state = AgentState.READY;
    final var metrics = AgentMetrics.empty();
    final var toolDefinitions = List.of();

    return Stream.of(
        arguments(null, metrics, toolDefinitions, "Agent state must not be null"),
        arguments(state, null, toolDefinitions, "Agent metrics must not be null"),
        arguments(state, metrics, null, "Tool definitions must not be null"));
  }
}
