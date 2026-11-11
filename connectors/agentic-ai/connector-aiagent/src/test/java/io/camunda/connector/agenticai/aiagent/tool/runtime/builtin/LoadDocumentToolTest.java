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
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.AgentDocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
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
      new RuntimeTools(List.of(tool), objectMapper, new DocumentParameterResolver(objectMapper));
  private final LocalToolCallExecutor executor =
      new LocalToolCallExecutor(runtimeTools, TestDocuments.objectHandleRenderer());
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

  private static AgentDocumentContent referenceTo(AgentDocument entry) {
    return AgentDocumentContent.of(entry.id());
  }

  private static String refOf(AgentDocument entry) {
    final var fileName = AgentDocumentHandle.of(entry).fileName();
    return fileName == null ? entry.id().value() : fileName + " (" + entry.id().value() + ")";
  }

  /** The sentence the agent writes into the tool result for a document that is loaded. */
  private static Content follows(AgentDocument entry) {
    return textContent("The content of " + refOf(entry) + " follows in the next message.");
  }

  /** The sentence the agent writes into the tool result for a document that is not loaded. */
  private static Content notLoaded(AgentDocument entry, String reason) {
    return textContent("The content of " + refOf(entry) + " is not loaded. " + reason);
  }

  /** What the agent does with a load_document call: the entry and the input of the next turn. */
  private record Run(ToolCallBatch.Entry entry, TurnInput input) {

    ToolCallResultContent result() {
      return ((ToolCallResultMessage) input.messages().getFirst()).results().getFirst();
    }

    /** What the agent adds after the tool results: the content of the documents it loads. */
    List<Content> addedContent() {
      return input.messages().size() < 2
          ? List.of()
          : ((UserMessage) input.messages().get(1)).content().stream().skip(1).toList();
    }
  }

  @Test
  void definitionHasASingleRequiredDocumentParameterAndSaysTheResultTellsWhyADocumentIsNotLoaded() {
    final var definition = tool.definition();

    assertThat(LoadDocumentTool.NAME).isEqualTo("c8__load_document");
    assertThat(definition.description())
        .isEqualTo(
            "Loads the content of a document that you see as a handle in the conversation: a JSON"
                + " object with \"type\": \"camunda.agenticai.document\" and an \"id\" that"
                + " starts with \"doc_\". Do not call it when the conversation has no such handle."
                + " Only text, PDF and image documents that are not too large can be loaded; the"
                + " result tells you when a document is not loaded and why. A document that is"
                + " already in the conversation is not loaded again.");
    // the stored schema is the schema of a document handle
    assertThat(definition.inputSchema())
        .isEqualTo(
            Map.of(
                "type",
                "object",
                "properties",
                Map.of("id", DocumentSchemas.document("The handle of the document to load.")),
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
    // the tool returns no text of its own, the agent writes the sentence
    assertThat(run.entry().result().content()).isEmpty();
    assertThat(run.result().content()).containsExactly(follows(entry));
    assertThat(run.result().properties()).isEmpty();
    assertThat(run.addedContent())
        .containsExactly(referenceTo(entry).withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void loadsADocumentPassedAsAStringWithItsHandle() {
    final var entry = camundaEntry("doc-1", "text/plain", 10L);

    final var withHandle =
        run(
            List.of(entry),
            Set.of(),
            callWith("1", "{\"type\":\"camunda.agenticai.document\",\"id\":\"doc-1\"}"));

    assertThat(withHandle.addedContent())
        .containsExactly(referenceTo(entry).withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void asksForADocumentThatAlreadyHasContentInTheConversationAndTheAgentSaysSo() {
    final var entry = camundaEntry("doc-1", "text/plain", 10L);
    final var withContent = referenceTo(entry).withDelivery(DocumentDelivery.CONTENT);
    final var inConversation =
        UserMessage.builder().content(List.of(withContent)).build().documentIdsWithContent();

    final var run = run(List.of(entry), inConversation, call("1", "doc-1"));

    assertThat(run.entry().effects())
        .containsExactly(new ToolCallEffect.AddDocumentContent(AgentDocumentId.of("doc-1")));
    assertThat(run.result().content())
        .containsExactly(textContent("The content of doc-1 is already in the conversation."));
    assertThat(run.addedContent()).isEmpty();
  }

  @Test
  void loadsADocumentThatIsOnlyAReferenceInTheConversation() {
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
  void theResultSaysWhyADocumentAboveTheTextLimitIsNotLoaded() {
    final var entry = camundaEntry("doc-1", "text/csv", 300L * 1024);

    final var run = run(List.of(entry), Set.of(), call("1", "doc-1"));

    assertThat(run.result().content())
        .containsExactly(
            notLoaded(
                entry,
                "The document is too large to load (300 KB, limit 256 KB). If one of your tools can process the document, pass the handle to it."));
    assertThat(run.addedContent()).isEmpty();
  }

  @Test
  void theResultSaysAnUnreadableDocumentOfUnknownSizeIsNotLoaded() {
    // the store holds no content for the document
    final var entry = camundaEntry("doc-1", "text/plain", null);

    final var run = run(List.of(entry), Set.of(), call("1", "doc-1"));

    assertThat(run.result().content())
        .containsExactly(notLoaded(entry, "The document could not be read."));
    assertThat(run.addedContent()).isEmpty();
  }

  @Test
  void answersAMissingIdWithAnErrorWithoutEffects() {
    final var context = RuntimeToolContextImpl.of(withConfiguration(CONFIGURATION), WITH_DOCUMENTS);
    final var arguments =
        new RuntimeToolCallImpl("1", LoadDocumentTool.NAME, Map.of(), objectMapper);

    final var result = tool.call(context, arguments);

    assertThat(result)
        .isEqualTo(
            RuntimeToolResult.error(
                "Parameter 'id' must be the handle of a document from the conversation: a JSON"
                    + " object such as {\"type\":\"camunda.agenticai.document\",\"id\":\"doc_…\"}."));
  }

  private static ToolCall call(String id, String documentId) {
    return callWith(id, Map.of("type", AgentDocumentHandle.TYPE, "id", documentId));
  }

  private static ToolCall callWith(String id, Object idArgument) {
    return new ToolCall(id, LoadDocumentTool.NAME, Map.of("id", idArgument))
        .withKind(ToolKind.RUNTIME);
  }

  private Run run(List<AgentDocument> entries, Set<AgentDocumentId> idsWithContent, ToolCall call) {
    final var agentContext = WITH_DOCUMENTS.withDocuments(AgentDocuments.of(entries));
    final var batchEntries =
        executor.execute(withConfiguration(CONFIGURATION), agentContext, List.of(call));
    final var batch = ToolCallBatch.of(List.of(call)).withEntries(batchEntries);
    final var input =
        inputBuilder.build(agentContext, batch, idsWithContent, TestDocuments.documentFactory());
    return new Run(batchEntries.getFirst(), input);
  }
}
