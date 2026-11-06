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
import io.camunda.connector.agenticai.aiagent.agent.LocalToolCallExecutor;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallBatch;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultsInputBuilder;
import io.camunda.connector.agenticai.aiagent.agent.TurnInput;
import io.camunda.connector.agenticai.aiagent.api.tool.DocumentSchemas;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolCallImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolContextImpl;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTools;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import io.camunda.connector.runtime.core.intrinsic.DefaultIntrinsicFunctionExecutor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The {@code c8__load_document} tool run by the framework, the way the agent runs it: the runtime
 * tools runs the call and the input builder applies the effect of the result.
 */
class LoadDocumentToolTest {

  private static final AgentConfiguration CONFIGURATION =
      new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null);

  private static final AgentContext WITH_DOCUMENTS =
      AgentContext.empty()
          .withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS, AgentFeatures.DOCUMENTS));

  private final ObjectMapper objectMapper = objectMapperFor(InMemoryDocumentStore.INSTANCE);
  private final LoadDocumentTool tool = new LoadDocumentTool();
  private final RuntimeTools runtimeTools =
      new RuntimeTools(
          List.of(tool),
          objectMapper,
          new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE),
          new DocumentParameterResolver());
  private final LocalToolCallExecutor executor = new LocalToolCallExecutor(runtimeTools);
  private final ToolCallResultsInputBuilder inputBuilder =
      new ToolCallResultsInputBuilder(TestDocuments.documentContentDelivery());

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

  private static AgentDocument camundaEntry(String id, String contentType, @Nullable Long size) {
    final var reference =
        new CamundaDocumentReferenceModel(
            "in-memory",
            id,
            null,
            new CamundaDocumentMetadataModel(contentType, null, size, id, null, null, null));
    return new AgentDocument(
        AgentDocumentId.of(id),
        AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID),
        reference);
  }

  /** A document that is a URL: nothing of it is known without downloading it. */
  private static AgentDocument externalEntry(String id) {
    final var reference =
        new ExternalDocumentReferenceModel(
            "https://files.example.com/" + id + "?token=secret", id + ".pdf");
    return new AgentDocument(
        AgentDocumentId.of(id), AgentDocumentOrigin.toolCall("search", "call-1"), reference);
  }

  private static AgentDocumentContent referenceTo(AgentDocument entry) {
    return AgentDocumentContent.of(entry.id());
  }

  /** The answer of a request: the document as a reference block between two texts. */
  private static List<Content> requestedAnswer(AgentDocument entry) {
    return List.of(
        textContent("Requested the content of the document."),
        referenceTo(entry),
        textContent("It follows in the next message, or a note says why it cannot be loaded."));
  }

  private static String notLoaded(String id, String reason) {
    return "The document %s %s.".formatted(id, reason);
  }

  private static String text(ToolCallResultContent result) {
    assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
    return ((TextContent) result.content().getFirst()).text();
  }

  /** What the agent does with a load_document call: the entry and the input of the next turn. */
  private record Run(ToolCallBatch.Entry entry, TurnInput input) {

    ToolCallResultContent result() {
      return ((ToolCallResultMessage) input.messages().getFirst()).results().getFirst();
    }

    /** What the agent adds after the tool results: the content of documents or notes. */
    List<Content> addedContent() {
      return input.messages().size() < 2
          ? List.of()
          : ((UserMessage) input.messages().get(1)).content().stream().skip(1).toList();
    }
  }

  @Test
  void definitionHasASingleRequiredDocumentParameterAndTellsAboutLargeDocuments() {
    final var definition = tool.definition();

    assertThat(LoadDocumentTool.NAME).isEqualTo("c8__load_document");
    assertThat(definition.description())
        .isEqualTo(
            "Loads a known document so that its content can be read in the conversation. Use it when"
                + " you need the content of a document that you only see as a <doc/> tag. It does not"
                + " load documents that are too large and documents that are external links, a note"
                + " tells you so: pass the tag of such a document to a tool that processes it"
                + " instead, for example a sandbox. Only text, PDF and image documents can be"
                + " loaded. A document that is already in the conversation is not loaded again.");
    // the stored schema holds the document format and no hint: the model providers get the hint
    assertThat(definition.inputSchema())
        .isEqualTo(
            Map.of(
                "type",
                "object",
                "properties",
                Map.of("id", DocumentSchemas.document("The document to load.")),
                "required",
                List.of("id")));
  }

  @Test
  void isOfferedToEveryAgentWithTheDocumentsFeatureOnly() {
    final var executionContext = withConfiguration(CONFIGURATION);

    assertThat(runtimeTools.enabledDefinitions(executionContext, WITH_DOCUMENTS))
        .extracting(definition -> definition.name())
        .containsExactly("c8__load_document");
    assertThat(
            runtimeTools.enabledDefinitions(
                executionContext,
                AgentContext.empty().withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS))))
        .isEmpty();
  }

  @Test
  void loadsAKnownDocumentAndAsksTheAgentForItsContent() {
    final var entry = camundaEntry("doc-1", "text/plain", 10L);

    final var run = run(List.of(entry), Set.of(), call("1", "doc-1"));

    assertThat(run.entry().effects())
        .containsExactly(new ToolCallEffect.AddDocumentContent(AgentDocumentId.of("doc-1")));
    assertThat(run.result().content()).isEqualTo(requestedAnswer(entry));
    assertThat(run.result().properties()).isEmpty();
    assertThat(run.addedContent())
        .containsExactly(referenceTo(entry).withDelivery(DocumentDelivery.CONTENT));
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
        .containsExactly(referenceTo(entry).withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void doesNotAddTheContentOfADocumentThatAlreadyHasContentInTheConversation() {
    final var entry = camundaEntry("doc-1", "text/plain", 10L);
    final var tagWithContent = referenceTo(entry).withDelivery(DocumentDelivery.CONTENT);
    final var inConversation =
        UserMessage.builder().content(List.of(tagWithContent)).build().documentIdsWithContent();

    final var run = run(List.of(entry), inConversation, call("1", "doc-1"));

    assertThat(text(run.result())).isEqualTo("Document doc-1 is already in the conversation.");
    assertThat(run.entry().effects()).isEmpty();
    assertThat(run.addedContent()).isEmpty();
  }

  @Test
  void loadsADocumentWhoseTagIsOnlyAReference() {
    final var entry = camundaEntry("doc-1", "text/plain", 10L);
    final var inConversation =
        UserMessage.builder().content(List.of(referenceTo(entry))).build().documentIdsWithContent();

    final var run = run(List.of(entry), inConversation, call("1", "doc-1"));

    assertThat(run.entry().effects())
        .containsExactly(new ToolCallEffect.AddDocumentContent(AgentDocumentId.of("doc-1")));
    assertThat(run.addedContent())
        .containsExactly(referenceTo(entry).withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void keepsTheResultAndAddsANoteForADocumentAboveTheTextLimitByItsKnownSize() {
    final var entry = camundaEntry("doc-1", "text/csv", 300L * 1024);

    final var run = run(List.of(entry), Set.of(), call("1", "doc-1"));

    assertThat(run.result().content()).isEqualTo(requestedAnswer(entry));
    assertThat(run.addedContent())
        .containsExactly(
            textContent(
                notLoaded(
                    "doc-1",
                    "is 300 KB, too large to load into the conversation (limit 256 KB); pass its tag to a tool that processes it")));
  }

  @Test
  void keepsTheResultAndAddsANoteForAnExternalDocument() {
    final var entry = externalEntry("ext-1");

    final var run = run(List.of(entry), Set.of(), call("1", "ext-1"));

    assertThat(run.result().content()).isEqualTo(requestedAnswer(entry));
    assertThat(run.addedContent())
        .containsExactly(
            textContent(
                notLoaded(
                    "ext-1",
                    "is an external document, which cannot be loaded into the conversation; pass its tag to a tool that processes it")));
  }

  @Test
  void keepsTheResultAndAddsANoteForAnUnreadableDocumentOfUnknownSize() {
    // the store holds no content for the document
    final var entry = camundaEntry("doc-1", "text/plain", null);

    final var run = run(List.of(entry), Set.of(), call("1", "doc-1"));

    assertThat(run.result().content()).isEqualTo(requestedAnswer(entry));
    assertThat(run.addedContent())
        .containsExactly(
            textContent(notLoaded("doc-1", "could not be read, so its content is not loaded")));
  }

  @Test
  void answersAMissingIdWithAnErrorWithoutEffects() {
    final var context =
        RuntimeToolContextImpl.of(
            withConfiguration(CONFIGURATION),
            WITH_DOCUMENTS,
            Set.of(),
            new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE));
    final var arguments =
        new RuntimeToolCallImpl("1", LoadDocumentTool.NAME, Map.of(), objectMapper);

    final var result = tool.call(context, arguments);

    assertThat(result)
        .isEqualTo(RuntimeToolResult.error("Parameter 'id' must be the tag of a known document."));
  }

  private static ToolCall call(String id, String documentId) {
    return callWith(id, "<doc id=\"" + documentId + "\" />");
  }

  private static ToolCall callWith(String id, String idArgument) {
    return new ToolCall(id, LoadDocumentTool.NAME, Map.of("id", idArgument))
        .withKind(ToolKind.RUNTIME);
  }

  private Run run(List<AgentDocument> entries, Set<AgentDocumentId> idsWithContent, ToolCall call) {
    final var agentContext = WITH_DOCUMENTS.withDocuments(AgentDocuments.of(entries));
    final var batchEntries =
        executor.execute(
            withConfiguration(CONFIGURATION), agentContext, idsWithContent, List.of(call));
    final var batch = ToolCallBatch.of(List.of(call)).withEntries(batchEntries);
    final var input = inputBuilder.build(agentContext, batch, idsWithContent);
    return new Run(batchEntries.getFirst(), input);
  }
}
