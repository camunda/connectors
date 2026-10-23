/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime.builtin;

import static io.camunda.connector.agenticai.aiagent.TestAgentExecutionContext.withConfiguration;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolExecution;
import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolExecutorImpl;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultsInputBuilder;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultsInputBuilderImpl;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentLimits;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.MessageUtil;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolCallImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolContextImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The {@code c8__get_document} tool run by the framework, the way the agent runs it: the executor
 * runs the call and the input builder applies the effect of the result.
 */
class GetDocumentToolTest {

  private static final AgentConfiguration CONFIGURATION =
      new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null);

  private static final AgentContext WITH_DOCUMENTS =
      AgentContext.empty()
          .withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS));

  private final ObjectMapper objectMapper = objectMapperFor(InMemoryDocumentStore.INSTANCE);
  private final GetDocumentTool tool = new GetDocumentTool();
  private final RuntimeToolRegistryImpl registry =
      new RuntimeToolRegistryImpl(List.of(tool), objectMapper);
  private final RuntimeToolExecutorImpl executor =
      new RuntimeToolExecutorImpl(registry, objectMapper);
  private final ToolCallResultsInputBuilder inputBuilder =
      new ToolCallResultsInputBuilderImpl(
          new DocumentContentGuard(DocumentContentLimits.defaults()), objectMapper);

  private static ObjectMapper objectMapperFor(CamundaDocumentStore store) {
    final var copy = ConnectorsObjectMapperSupplier.getCopy();
    return copy.registerModules(
        new JacksonModuleDocumentDeserializer(
            new DocumentFactoryImpl(store),
            new DefaultIntrinsicFunctionExecutor(copy),
            DocumentModuleSettings.create()),
        new JacksonModuleFeelFunction(),
        new JacksonModuleDocumentSerializer());
  }

  private static DocumentRegistryEntry camundaEntry(
      String id, String contentType, @Nullable Long size) {
    final var metadata = new LinkedHashMap<String, Object>();
    metadata.put("contentType", contentType);
    metadata.put("fileName", id);
    if (size != null) {
      metadata.put("size", size);
    }
    final var reference = new LinkedHashMap<String, Object>();
    reference.put("camunda.document.type", "camunda");
    reference.put("storeId", "in-memory");
    reference.put("documentId", id);
    reference.put("metadata", metadata);
    return new DocumentRegistryEntry(DocumentId.of(id), DocumentSource.userMessage(), reference);
  }

  /** A document that is a URL: nothing of it is known without downloading it. */
  private static DocumentRegistryEntry externalEntry(String id) {
    final var reference = new LinkedHashMap<String, Object>();
    reference.put("camunda.document.type", "external");
    reference.put("url", "https://files.example.com/" + id + "?token=secret");
    reference.put("name", id + ".pdf");
    return new DocumentRegistryEntry(
        DocumentId.of(id), DocumentSource.toolCall("search", "call-1"), reference);
  }

  /** The answer of a load: the document as a reference block between two texts. */
  private static List<Content> loadedAnswer(DocumentRegistryEntry entry) {
    return List.of(
        textContent("Loaded the document."),
        DocumentReferences.from(entry),
        textContent("Its content follows in the next message."));
  }

  private static String text(ToolCallResultContent result) {
    assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
    return ((TextContent) result.content().getFirst()).text();
  }

  /** What the agent does with a get_document call: the execution and the input of the next turn. */
  private record Run(
      List<RuntimeToolExecution> executions, ToolCallResultsInputBuilder.Input input) {

    ToolCallResultContent result() {
      return ((ToolCallResultMessage) input.messages().getFirst()).results().getFirst();
    }

    /** The references with content the agent adds after the tool results. */
    List<Content> addedContent() {
      return input.messages().size() < 2
          ? List.of()
          : ((UserMessage) input.messages().get(1)).content().stream().skip(1).toList();
    }
  }

  @Test
  void definitionHasASingleRequiredDocumentParameterAndTellsAboutLargeDocuments() {
    final var definition = tool.definition();

    assertThat(GetDocumentTool.NAME).isEqualTo("c8__get_document");
    assertThat(definition.description())
        .contains("Loads a known document")
        .contains("too large")
        .contains("external links")
        .contains("pass the tag of such a document to a tool that processes it")
        .contains("sandbox");
    assertThat(definition.inputSchema().get("required")).isEqualTo(List.of("id"));
    assertThat(definition.inputSchema().get("properties"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsOnlyKeys("id");
    // the stored schema holds the document format and no hint: the model providers get the hint
    assertThat(definition.inputSchema().toString())
        .contains("format=camunda-document-id")
        .doesNotContain("Pass the tag of a known document");
  }

  @Test
  void isOfferedToEveryAgentWithTheDocumentsFeatureOnly() {
    final var executionContext = withConfiguration(CONFIGURATION);

    assertThat(registry.enabledToolDefinitions(executionContext, WITH_DOCUMENTS))
        .extracting(definition -> definition.name())
        .containsExactly("c8__get_document");
    assertThat(
            registry.enabledToolDefinitions(
                executionContext,
                AgentContext.empty().withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS))))
        .isEmpty();
  }

  @Test
  void loadsAKnownDocumentAndAsksTheAgentForItsContent() {
    final var entry = camundaEntry("doc-1", "text/plain", 10L);

    final var run = run(List.of(entry), Set.of(), call("1", "doc-1"));

    assertThat(run.executions().getFirst().result().documentContent())
        .containsExactly(DocumentId.of("doc-1"));
    assertThat(run.result().content()).isEqualTo(loadedAnswer(entry));
    assertThat(run.result().properties()).isEmpty();
    assertThat(run.addedContent())
        .containsExactly(DocumentReferences.from(entry).withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void loadsADocumentPassedAsItsTag() {
    final var entry = camundaEntry("doc-1", "text/plain", 10L);

    final var run =
        run(
            List.of(entry),
            Set.of(),
            callWith("1", "<doc id=\"doc-1\" contentType=\"text/plain\" />"));

    assertThat(run.addedContent())
        .containsExactly(DocumentReferences.from(entry).withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void doesNotAddTheContentOfADocumentThatAlreadyHasContentInTheConversation() {
    final var entry = camundaEntry("doc-1", "text/plain", 10L);
    final var tagWithContent =
        DocumentReferences.from(entry).withDelivery(DocumentDelivery.CONTENT);
    final var inConversation =
        MessageUtil.documentIdsWithContent(
            List.of(UserMessage.builder().content(List.of(tagWithContent)).build()));

    final var run = run(List.of(entry), inConversation, call("1", "doc-1"));

    assertThat(text(run.result())).isEqualTo("Document doc-1 is already in the conversation.");
    assertThat(run.executions().getFirst().result().documentContent()).isEmpty();
    assertThat(run.addedContent()).isEmpty();
  }

  @Test
  void loadsADocumentWhoseTagIsOnlyAReference() {
    final var entry = camundaEntry("doc-1", "text/plain", 10L);
    final var referenceTag = DocumentReferences.from(entry);
    final var inConversation =
        MessageUtil.documentIdsWithContent(
            List.of(UserMessage.builder().content(List.of(referenceTag)).build()));

    final var run = run(List.of(entry), inConversation, call("1", "doc-1"));

    assertThat(run.executions().getFirst().result().documentContent())
        .containsExactly(DocumentId.of("doc-1"));
    assertThat(run.addedContent())
        .containsExactly(DocumentReferences.from(entry).withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void rejectsADocumentAboveTheTextLimitByItsKnownSizeWithoutReadingIt() {
    final var run =
        run(List.of(camundaEntry("doc-1", "text/csv", 300L * 1024)), Set.of(), call("1", "doc-1"));

    assertThat(text(run.result()))
        .isEqualTo(
            "The document is 300 KB, too large to load into the conversation (limit 256 KB); pass its tag to a tool that processes it.");
    assertThat(run.addedContent()).isEmpty();
  }

  @Test
  void rejectsAnExternalDocument() {
    final var run = run(List.of(externalEntry("ext-1")), Set.of(), call("1", "ext-1"));

    assertThat(text(run.result()))
        .isEqualTo(
            "The document is an external document, which cannot be loaded into the conversation; pass its tag to a tool that processes it.");
    assertThat(run.addedContent()).isEmpty();
  }

  @Test
  void answersAnUnreadableDocumentOfUnknownSizeWithAnError() {
    // the store holds no content for the document of the registry
    final var run =
        run(List.of(camundaEntry("doc-1", "text/plain", null)), Set.of(), call("1", "doc-1"));

    assertThat(text(run.result())).isEqualTo("The document could not be read; it is not loaded.");
    assertThat(run.addedContent()).isEmpty();
  }

  @Test
  void answersAMissingIdWithAnErrorWithoutEffects() {
    final var context =
        RuntimeToolContextImpl.of(withConfiguration(CONFIGURATION), WITH_DOCUMENTS, objectMapper);
    final var arguments =
        new RuntimeToolCallImpl("1", GetDocumentTool.NAME, Map.of(), objectMapper);

    final var result = tool.call(arguments, context);

    assertThat(result).isInstanceOf(RuntimeToolResult.Error.class);
    assertThat(result.content())
        .containsExactly(textContent("Parameter 'id' must be the tag of a known document."));
    assertThat(result.documentContent()).isEmpty();
  }

  private static ToolCall call(String id, String documentId) {
    return callWith(id, "<doc id=\"" + documentId + "\" />");
  }

  private static ToolCall callWith(String id, String idArgument) {
    return new ToolCall(id, GetDocumentTool.NAME, Map.of("id", idArgument))
        .withKind(ToolKind.RUNTIME);
  }

  private Run run(
      List<DocumentRegistryEntry> entries, Set<DocumentId> idsWithContent, ToolCall call) {
    final var agentContext = WITH_DOCUMENTS.withDocuments(entries);
    final var executions =
        executor.execute(
            withConfiguration(CONFIGURATION), agentContext, List.of(call), idsWithContent);
    final var input =
        inputBuilder.build(
            agentContext,
            executions.stream().map(ToolCallResultsInputBuilder.Entry::of).toList(),
            idsWithContent);
    return new Run(executions, input);
  }
}
