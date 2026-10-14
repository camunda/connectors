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
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    assertThat(context.documents()).isEmpty();

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

    assertThat(context.documents()).isEmpty();
    assertThat(context.state()).isEqualTo(AgentState.READY);
    assertThat(context.schemaVersion()).isEqualTo(AgentContext.CURRENT_SCHEMA_VERSION);
  }

  @Test
  void documentsAreSerializedAndDeserialized() throws JsonProcessingException {
    final var entry =
        new DocumentRegistryEntry(
            "doc-1",
            DocumentSource.toolCall("search", "call-1", null),
            Map.of(
                "camunda.document.type", "camunda", "storeId", "in-memory", "documentId", "doc-1"));
    final var context = AgentContext.builder().documents(List.of(entry)).build();

    final var serialized = objectMapper.writeValueAsString(context);
    final var deserialized = objectMapper.readValue(serialized, AgentContext.class);

    assertThat(serialized).contains("\"documents\"");
    assertThat(deserialized.documents()).containsExactly(entry);
    assertThat(deserialized).isEqualTo(context);
  }

  @Test
  void responseDocumentsDefaultToEmptyAndAreOmittedFromJsonWhenEmpty()
      throws JsonProcessingException {
    final var context = AgentContext.empty();
    assertThat(context.responseDocuments()).isEmpty();

    assertThat(objectMapper.writeValueAsString(context)).doesNotContain("responseDocuments");
  }

  @Test
  void agentContextWithoutResponseDocumentsFieldBindsToEmptyMap() throws JsonProcessingException {
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

    assertThat(context.responseDocuments()).isEmpty();
    assertThat(context.schemaVersion()).isEqualTo(AgentContext.CURRENT_SCHEMA_VERSION);
  }

  @Test
  void responseDocumentsAreSerializedAndDeserializedInInsertionOrder()
      throws JsonProcessingException {
    final var responseDocuments = new LinkedHashMap<String, ResponseDocumentSelection>();
    for (final var key : List.of("zeta", "alpha", "mid", "beta", "omega", "gamma", "delta")) {
      responseDocuments.put(key, new ResponseDocumentSelection("doc-" + key, null));
    }
    responseDocuments.put("alpha", new ResponseDocumentSelection("doc-alpha-2", "Replaced"));
    final var context = AgentContext.builder().responseDocuments(responseDocuments).build();

    final var serialized = objectMapper.writeValueAsString(context);
    final var deserialized = objectMapper.readValue(serialized, AgentContext.class);

    assertThat(serialized)
        .contains("\"responseDocuments\":{\"zeta\":{\"document\":\"doc-zeta\"},\"alpha\":");
    assertThat(serialized).contains("{\"document\":\"doc-alpha-2\",\"description\":\"Replaced\"}");
    assertThat(deserialized.responseDocuments().keySet())
        .containsExactly("zeta", "alpha", "mid", "beta", "omega", "gamma", "delta");
    assertThat(deserialized).isEqualTo(context);
  }

  @Test
  void pendingRuntimeToolCallResultsDefaultToEmptyAndAreOmittedFromJsonWhenEmpty()
      throws JsonProcessingException {
    final var context = AgentContext.empty();
    assertThat(context.pendingRuntimeToolCallResults()).isEmpty();

    assertThat(objectMapper.writeValueAsString(context))
        .doesNotContain("pendingRuntimeToolCallResults");
  }

  @Test
  void agentContextWithoutPendingRuntimeToolCallResultsFieldBindsToEmptyList()
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

    assertThat(context.pendingRuntimeToolCallResults()).isEmpty();
    assertThat(context.schemaVersion()).isEqualTo(AgentContext.CURRENT_SCHEMA_VERSION);
  }

  @Test
  void pendingRuntimeToolCallResultsAreSerializedAndDeserialized() throws JsonProcessingException {
    final var textResult =
        ToolCallResultContent.builder()
            .id("call-1")
            .name("c8__return_document")
            .content(
                List.of(TextContent.textContent("Returned the document under the key 'report'.")))
            .completedAt(OffsetDateTime.parse("2026-10-05T10:15:30.123+02:00"))
            .build();
    final var structuredResult =
        ToolCallResultContent.builder()
            .id("call-2")
            .name("load_skill")
            .content(
                List.of(
                    ObjectContent.objectContent(
                        Map.of("skill", "pdf", "files", List.of("a.md", "b.md")))))
            .completedAt(OffsetDateTime.parse("2026-10-05T10:15:30.456+02:00"))
            .build();
    final var context =
        AgentContext.builder()
            .pendingRuntimeToolCallResults(List.of(textResult, structuredResult))
            .build();

    final var serialized = objectMapper.writeValueAsString(context);
    final var deserialized = objectMapper.readValue(serialized, AgentContext.class);

    assertThat(serialized).contains("\"pendingRuntimeToolCallResults\"");
    assertThat(deserialized.pendingRuntimeToolCallResults())
        .usingRecursiveComparison()
        .isEqualTo(List.of(textResult, structuredResult));
  }

  @Test
  void featuresDefaultToEmptyAndAreOmittedFromJsonWhenEmpty() throws JsonProcessingException {
    final var context = AgentContext.empty();

    assertThat(context.features()).isEmpty();
    assertThat(context.supportsDocuments()).isFalse();
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
    assertThat(context.supportsDocuments()).isFalse();
    assertThat(context.schemaVersion()).isEqualTo(AgentContext.CURRENT_SCHEMA_VERSION);
    assertThat(objectMapper.writeValueAsString(context)).doesNotContain("features");
  }

  @Test
  void newInstanceFeaturesContainTheRuntimeToolsAndDocumentsFeatures() {
    final var context = AgentContext.empty().withFeatures(AgentContext.newInstanceFeatures());

    assertThat(context.features()).containsExactly("runtimeTools", "documents");
    assertThat(context.supportsRuntimeTools()).isTrue();
    assertThat(context.supports(AgentContext.FEATURE_DOCUMENTS)).isTrue();
    assertThat(context.supportsDocuments()).isTrue();
    assertThat(context.supports("other")).isFalse();
  }

  @Test
  void theDocumentsFeatureImpliesTheRuntimeToolsFeature() {
    final var documentsOnly =
        AgentContext.empty().withFeatures(new LinkedHashSet<>(List.of("documents")));
    final var runtimeToolsOnly =
        AgentContext.empty().withFeatures(new LinkedHashSet<>(List.of("runtimeTools")));

    assertThat(documentsOnly.supportsRuntimeTools()).isTrue();
    assertThat(runtimeToolsOnly.supportsRuntimeTools()).isTrue();
    assertThat(runtimeToolsOnly.supportsDocuments()).isFalse();
    assertThat(AgentContext.empty().supportsRuntimeTools()).isFalse();
  }

  @Test
  void featuresAreSerializedAndDeserializedInInsertionOrder() throws JsonProcessingException {
    final var features = new LinkedHashSet<String>();
    for (final var feature : List.of("zeta", "alpha", "documents", "beta", "omega", "gamma")) {
      features.add(feature);
    }
    final var context = AgentContext.builder().features(features).build();

    final var serialized = objectMapper.writeValueAsString(context);
    final var deserialized = objectMapper.readValue(serialized, AgentContext.class);

    assertThat(serialized)
        .contains("\"features\":[\"zeta\",\"alpha\",\"documents\",\"beta\",\"omega\",\"gamma\"]");
    assertThat(deserialized.features())
        .containsExactly("zeta", "alpha", "documents", "beta", "omega", "gamma");
    assertThat(deserialized).isEqualTo(context);
  }

  @Test
  void featuresSurviveCopiesThatChangeOtherComponents() {
    final var features = new LinkedHashSet<String>();
    for (final var feature : List.of("zeta", "alpha", "documents", "beta", "omega", "gamma")) {
      features.add(feature);
    }
    final var context = AgentContext.builder().features(features).build();

    final var copy =
        context
            .withState(AgentState.READY)
            .withProperty("hello", "world")
            .withMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20), 0))
            .withPendingRuntimeToolCallResults(List.of());

    assertThat(copy.features())
        .containsExactly("zeta", "alpha", "documents", "beta", "omega", "gamma");
    assertThat(copy.supportsDocuments()).isTrue();
  }

  @Test
  void featuresAreCopiedDefensively() {
    final var features = new LinkedHashSet<>(List.of("documents"));
    final var context = AgentContext.builder().features(features).build();

    features.add("other");

    assertThat(context.features()).containsExactly("documents");
  }

  @ParameterizedTest
  @MethodSource("invalidConstructorParameters")
  void throwsExceptionOnInvalidConstructorParameters(
      AgentState state,
      AgentMetrics metrics,
      List<ToolDefinition> toolDefinitions,
      String exceptionMessage) {
    assertThatThrownBy(
            () ->
                new AgentContext(
                    AgentContext.CURRENT_SCHEMA_VERSION,
                    state,
                    null,
                    metrics,
                    toolDefinitions,
                    InProcessConversationContext.builder("test-conversation").build(),
                    List.of(),
                    new LinkedHashMap<>(),
                    List.of(),
                    new LinkedHashSet<>(),
                    Map.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage(exceptionMessage);
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
