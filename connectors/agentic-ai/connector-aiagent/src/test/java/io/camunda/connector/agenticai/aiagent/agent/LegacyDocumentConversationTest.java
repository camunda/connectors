/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.TestAgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.TestMessagesFixture;
import io.camunda.connector.agenticai.aiagent.conversation.AgentConversation;
import io.camunda.connector.agenticai.aiagent.conversation.AgentInput;
import io.camunda.connector.agenticai.aiagent.conversation.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentLimits;
import io.camunda.connector.agenticai.aiagent.document.DocumentExpander;
import io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.memory.versioning.AgentContextSchemaMigration;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.GatewayToolHandlerRegistry;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.StubRuntimeTool;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Golden-fixture test: an agent instance created by Camunda 8.10 (no features, documents stored as
 * bare content or as a tag in the older attribute format followed by the content, no registry)
 * loads and continues with new documents of every kind in the form Camunda 8.10 composes them, and
 * never gets a registry, features, response documents or runtime tools.
 */
class LegacyDocumentConversationTest {

  private static final String FIXTURE =
      "/backwardcompatibility/camunda-8.10/agentContext-documents-inprocess.json";

  private final ObjectMapper objectMapper = TestObjectMapperSupplier.INSTANCE;
  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);

  private AgentConversationTurnInputComposerImpl composer;

  private AgentContext loadContext() throws IOException {
    try (var in = getClass().getResourceAsStream(FIXTURE)) {
      JsonNode tree = objectMapper.readTree(in);
      return AgentContextSchemaMigration.migrateAndBindAgentContext(tree, objectMapper);
    }
  }

  private static List<Message> messagesOf(AgentContext context) {
    return ((InProcessConversationContext) context.conversation()).messages();
  }

  private static AgentConfiguration configuration() {
    return new AgentConfiguration(
        TestMessagesFixture.TEST_CHAT_MODEL,
        TestMessagesFixture.TEST_SYSTEM_PROMPT,
        null,
        null,
        null,
        null,
        null);
  }

  private Document newDocument(String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from("new".getBytes(StandardCharsets.UTF_8))
            .contentType("text/plain")
            .fileName(fileName)
            .build());
  }

  private static String legacyTag(String toolAttributes, Document document) {
    final var reference =
        (io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference)
            document.reference();
    return "<doc %sdocumentId=\"%s\" storeId=\"in-memory\" contentType=\"text/plain\" fileName=\"%s\" />"
        .formatted(toolAttributes, reference.getDocumentId(), document.metadata().getFileName());
  }

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    final var gatewayToolHandlers = mock(GatewayToolHandlerRegistry.class);
    when(gatewayToolHandlers.transformToolCallResults(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(1));
    when(gatewayToolHandlers.handlerForToolDefinition(any())).thenReturn(Optional.empty());
    composer =
        new AgentConversationTurnInputComposerImpl(
            gatewayToolHandlers,
            new ToolCallResultsInputBuilderImpl(
                new DocumentContentGuard(DocumentContentLimits.defaults()),
                TestObjectMapperSupplier.INSTANCE),
            new DocumentContentGuard(DocumentContentLimits.defaults()));
  }

  @Test
  void loadsWithoutFeaturesAndWithoutARegistry() throws IOException {
    final var context = loadContext();

    assertThat(context.features()).isEmpty();
    assertThat(context.supports(AgentFeatures.DOCUMENTS)).isFalse();
    assertThat(context.documents()).isEmpty();
    assertThat(context.responseDocuments()).isEmpty();
  }

  @Test
  void composesUserPromptAndEventDocumentsInTheFormOfCamunda810() throws IOException {
    final var context = loadContext();
    final var promptDocument = newDocument("prompt.txt");
    final var eventDocument = newDocument("event.txt");
    final var eventResult =
        ToolCallResult.builder().content(Map.of("attachment", eventDocument)).build();

    final var composition =
        composer.compose(
            configuration(),
            context,
            TurnReconstructor.reconstruct(messagesOf(context), context.metadata()),
            AgentInput.from(
                new UserPromptConfiguration("Next", List.of(promptDocument)),
                List.of(eventResult)));

    final var nextTurn = (CompositionResult.NextTurn) composition;
    assertThat(nextTurn.documents()).isEmpty();
    assertThat(nextTurn.messages()).hasSize(2);

    // the prompt document is bare content, without a tag
    final var prompt = (UserMessage) nextTurn.messages().get(0);
    assertThat(prompt.content())
        .containsExactly(textContent("Next"), DocumentContent.documentContent(promptDocument));

    // the event document is the tag in the older attribute format followed by the content
    final var event = (UserMessage) nextTurn.messages().get(1);
    assertThat(event.content())
        .containsExactly(
            ObjectContent.objectContent(Map.of("attachment", eventDocument)),
            textContent(AgentConversationTurnInputComposerImpl.EVENT_DOCUMENTS_PREAMBLE),
            textContent(legacyTag("", eventDocument)),
            DocumentContent.documentContent(eventDocument));
    assertThat(event.content())
        .filteredOn(TextContent.class::isInstance)
        .allSatisfy(text -> assertThat(((TextContent) text).metadata()).isNull());
  }

  @Test
  void composesToolResultDocumentsInTheFormOfCamunda810() throws IOException {
    final var context = loadContext();
    final var messages = new ArrayList<>(messagesOf(context));
    messages.add(
        TestMessagesFixture.assistantMessage(
            "Fetching.",
            List.of(
                ToolCall.builder()
                    .id("call-2")
                    .name("Download_A_File")
                    .arguments(Map.of("url", "https://example.com/second.pdf"))
                    .build())));
    final var document = newDocument("second.txt");
    final var toolResult =
        ToolCallResult.builder()
            .id("call-2")
            .name("Download_A_File")
            .content(Map.of("file", document))
            .build();

    final var composition =
        composer.compose(
            configuration(),
            context,
            TurnReconstructor.reconstruct(messages, context.metadata()),
            AgentInput.from(new UserPromptConfiguration("", List.of()), List.of(toolResult)));

    final var nextTurn = (CompositionResult.NextTurn) composition;
    assertThat(nextTurn.documents()).isEmpty();
    assertThat(nextTurn.messages()).hasSize(2);
    assertThat(nextTurn.messages().get(0)).isInstanceOf(ToolCallResultMessage.class);

    final var documentMessage = (UserMessage) nextTurn.messages().get(1);
    assertThat(documentMessage.content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilderImpl.TOOL_CALL_DOCUMENTS_PREAMBLE),
            textContent(legacyTag("toolName=\"Download_A_File\" toolCallId=\"call-2\" ", document)),
            DocumentContent.documentContent(document));
    assertThat(documentMessage.metadata())
        .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
  }

  @Test
  void continuesTheTurnWithoutChangingTheStoredMessagesOrAddingState() throws IOException {
    final var context = loadContext();
    final var messages = messagesOf(context);
    final JsonNode messagesBefore = objectMapper.valueToTree(messages.subList(1, messages.size()));
    final var configuration = configuration();
    final var previous = TurnReconstructor.reconstruct(messages, context.metadata());
    final var nextTurn =
        (CompositionResult.NextTurn)
            composer.compose(
                configuration,
                context,
                previous,
                AgentInput.from(
                    new UserPromptConfiguration("Next", List.of(newDocument("next.txt"))),
                    List.of()));

    final var conversation =
        AgentConversation.rehydrate(configuration, context, previous, null, nextTurn.messages());
    final var snapshot =
        conversation
            .window(100, new DocumentExpander(objectMapper).windowDecorator(conversation))
            .messages();

    // no tag is inserted or rewritten: the window holds the stored messages and the new input as
    // they are
    assertThat(snapshot).hasSize(messages.size() - 1 + 1);
    assertThat((JsonNode) objectMapper.valueToTree(snapshot.subList(0, messages.size() - 1)))
        .isEqualTo(messagesBefore);
    assertThat(((UserMessage) snapshot.getLast()).content())
        .satisfiesExactly(
            text -> assertThat(text).isEqualTo(textContent("Next")),
            content -> assertThat(content).isInstanceOf(DocumentContent.class));

    // the persisted shape holds the document content, and no registry, features or response
    // documents
    final var stored = conversation.allMessages().getLast();
    assertThat(objectMapper.valueToTree(stored).at("/content/1/type").asText())
        .isEqualTo("document");
    final var storedContext = conversation.toAgentContext();
    assertThat(storedContext.features()).isEmpty();
    assertThat(storedContext.documents()).isEmpty();
    assertThat(storedContext.responseDocuments()).isEmpty();
    final var serialized = objectMapper.valueToTree(storedContext);
    assertThat(serialized.has("features")).isFalse();
    assertThat(serialized.has("documents")).isFalse();
    assertThat(serialized.has("responseDocuments")).isFalse();
  }

  @Test
  void rendersTheDocumentsOfToolResultsAsTheirReferenceLikeCamunda810() throws IOException {
    final var context = loadContext();
    final var configuration = configuration();
    final var previous = TurnReconstructor.reconstruct(messagesOf(context), context.metadata());
    final var conversation =
        AgentConversation.rehydrate(
            configuration,
            context,
            previous,
            null,
            List.of(TestMessagesFixture.userMessage("Next")));

    final var snapshot =
        conversation
            .window(100, new DocumentExpander(objectMapper).windowDecorator(conversation))
            .messages();

    final var toolResult =
        snapshot.stream()
            .filter(ToolCallResultMessage.class::isInstance)
            .map(ToolCallResultMessage.class::cast)
            .findFirst()
            .orElseThrow();
    final var original =
        messagesOf(context).stream()
            .filter(ToolCallResultMessage.class::isInstance)
            .map(ToolCallResultMessage.class::cast)
            .findFirst()
            .orElseThrow();
    final var content = (ObjectContent) toolResult.results().getFirst().content().getFirst();
    final var originalContent = (ObjectContent) original.results().getFirst().content().getFirst();

    // the tool result keeps its documents, which the object mapper writes as their reference
    assertThat(objectMapper.writeValueAsString(content.content()))
        .isEqualTo(objectMapper.writeValueAsString(originalContent.content()))
        .contains("\"documentId\":\"22222222-2222-4222-8222-222222222222\"")
        .doesNotContain("<doc");
  }

  @Test
  void offersNoRuntimeToolAndTagsEveryCallAsAProcessCall() throws IOException {
    final var context = loadContext();
    final var configuration = configuration();
    final var registry =
        new RuntimeToolRegistryImpl(
            List.of(StubRuntimeTool.of("acme_lookup", (call, toolContext) -> null)),
            TestObjectMapperSupplier.INSTANCE);

    final var executionContext = TestAgentExecutionContext.withConfiguration(configuration);
    assertThat(registry.enabledToolDefinitions(executionContext, context)).isEmpty();
    assertThat(registry.findEnabled(executionContext, context, "acme_lookup")).isEmpty();

    final var calls =
        List.of(
            ToolCall.builder()
                .id("call-3")
                .name("acme_lookup")
                .arguments(Map.of("key", "k", "document", "d"))
                .build());
    final var tagged =
        registry.tagToolCalls(context, AssistantMessage.builder().toolCalls(calls).build());

    assertThat(tagged.toolCalls()).containsExactlyElementsOf(calls);
    assertThat(tagged.toolCalls()).extracting(ToolCall::kind).containsOnly(ToolKind.PROCESS);
  }
}
