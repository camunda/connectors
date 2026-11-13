/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent.objectContent;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.agent.ToolCallBatch.Entry;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.api.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferenceXmlTag;
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
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolExecution;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolCallResultsInputBuilderTest {

  private static final OffsetDateTime COMPLETED_AT = OffsetDateTime.parse("2026-10-05T10:00:00Z");

  private static final AgentContext WITHOUT_DOCUMENTS = AgentContext.empty();
  private static final AgentContext WITH_DOCUMENTS =
      AgentContext.empty().withFeatures(List.of(AgentFeatures.DOCUMENTS));

  private final ToolCallResultsInputBuilder builder = new ToolCallResultsInputBuilder();

  private final Document first = createDocument("first", "first.txt");
  private final Document nested = createDocument("nested", "nested.txt");
  private final Document third = createDocument("third", "third.txt");
  private final Document known = createDocument("known", "known.txt");

  private static ToolCallResultContent result(String id, String name, Content... content) {
    return ToolCallResultContent.builder()
        .id(id)
        .name(name)
        .content(List.of(content))
        .completedAt(COMPLETED_AT)
        .build();
  }

  private static AgentDocumentId idOf(Document document) {
    return AgentDocumentId.of(document);
  }

  /** The file name and the id of a document as the sentences about its content name it. */
  private static String refOf(Document document) {
    return AgentDocument.Descriptors.of((CamundaDocumentReference) document.reference()).fileName()
        + " ("
        + idOf(document).value()
        + ")";
  }

  private static Content followsSentence(Document document) {
    return textContent("The content of " + refOf(document) + " follows in the next message.");
  }

  private static Content alreadySentence(Document document) {
    return textContent("The content of " + refOf(document) + " is already in the conversation.");
  }

  private static AgentContext knowing(Document... documents) {
    return WITH_DOCUMENTS.withDocuments(
        AgentDocuments.empty()
            .register(
                Arrays.stream(documents)
                    .map(document ->
                        AgentDocument.of(
                            document, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID)))
                    .toList()));
  }

  private static Entry runtimeEntry(String id, String name, RuntimeToolResult toolResult) {
    final var call = new ToolCall(id, name, Map.of()).withKind(ToolKind.RUNTIME);
    final var execution = new RuntimeToolExecution(call, toolResult, COMPLETED_AT, COMPLETED_AT);
    return switch (toolResult) {
      case RuntimeToolResult.Success success ->
          entryWithEffects(execution.toolCallResult(), success.effects());
      case RuntimeToolResult.Error ignored -> Entry.from(execution.toolCallResult());
    };
  }

  private static Entry entryOf(ToolCallResultContent result, List<Document> documents) {
    return new Entry(result, documents, List.of());
  }

  private static ToolCallBatch batchOf(Entry... entries) {
    final var calls =
        Arrays.stream(entries)
            .map(entry -> new ToolCall(entry.result().id(), entry.result().name(), Map.of()))
            .toList();
    return ToolCallBatch.of(calls).withEntries(List.of(entries));
  }

  private ToolCallBatch entries() {
    return batchOf(
        entryOf(
            result("c1", "alpha", textContent("one"), DocumentContent.documentContent(first)),
            List.of(first)),
        entryOf(result("c2", "beta", textContent("two")), List.of()),
        entryOf(
            result("c3", "gamma", objectContent(Map.of("file", nested, "again", third))),
            List.of(nested, third)));
  }

  @Test
  void withTheDocumentsFeatureTheResultsAreOneMessageAndTheDocumentsAreReportedInCallOrder() {
    final var input = builder.build(WITH_DOCUMENTS, entries(), Set.of());

    assertThat(input.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    assertThat(input.documents())
        .containsExactly(
            AgentDocument.of(first, AgentDocumentOrigin.toolCall("alpha", "c1")),
            AgentDocument.of(nested, AgentDocumentOrigin.toolCall("gamma", "c3")),
            AgentDocument.of(third, AgentDocumentOrigin.toolCall("gamma", "c3")));
  }

  @Test
  void withTheDocumentsFeatureADocumentThatIsAWholeResultIsItsTagAndANestedOneStaysADocument() {
    final var input = builder.build(WITH_DOCUMENTS, entries(), Set.of());

    final var results = ((ToolCallResultMessage) input.messages().getFirst()).results();
    assertThat(results.get(0).content())
        .containsExactly(textContent("one"), AgentDocumentContent.of(AgentDocumentId.of(first)));
    assertThat(results.get(1).content()).containsExactly(textContent("two"));
    assertThat(results.get(2).content())
        .containsExactly(objectContent(Map.of("file", nested, "again", third)));
  }

  @Test
  void withTheDocumentsFeatureAResultWithoutDocumentsReportsNone() {
    final var input =
        builder.build(
            WITH_DOCUMENTS,
            batchOf(entryOf(result("c2", "beta", textContent("two")), List.of())),
            Set.of());

    assertThat(input.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    assertThat(input.documents()).isEmpty();
  }

  @Test
  void withoutTheDocumentsFeatureTheDocumentsFollowTheResultsInAMessageAndNoneIsReported() {
    final var input = builder.build(WITHOUT_DOCUMENTS, entries(), Set.of());

    assertThat(input.documents()).isEmpty();
    assertThat(input.messages()).hasSize(2);
    assertThat(input.messages().get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(input.messages().get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            message -> {
              assertThat(message.metadata())
                  .isEqualTo(TestDocuments.toolCallDocumentsMetadata(message));
              assertThat(message.content())
                  .containsExactly(
                      textContent(ToolCallResultsInputBuilder.TOOL_CALL_DOCUMENTS_PREAMBLE),
                      textContent(DocumentReferenceXmlTag.from(first, "c1", "alpha").toXml()),
                      DocumentContent.documentContent(first),
                      textContent(DocumentReferenceXmlTag.from(nested, "c3", "gamma").toXml()),
                      DocumentContent.documentContent(nested),
                      textContent(DocumentReferenceXmlTag.from(third, "c3", "gamma").toXml()),
                      DocumentContent.documentContent(third));
            });
    final var results = ((ToolCallResultMessage) input.messages().getFirst()).results();
    assertThat(results.get(0).content())
        .containsExactly(textContent("one"), DocumentContent.documentContent(first));
  }

  @Test
  void aSuccessfulResultThatNamesAKnownDocumentEndsWithASentenceAndItsContentFollowsInAMessage() {
    final var entry =
        runtimeEntry(
            "c1", "alpha", RuntimeToolResult.success("loaded").withDocumentContent(idOf(known)));

    final var input = builder.build(knowing(known), batchOf(entry), Set.of());

    assertThat(input.messages()).hasSize(2);
    assertThat(input.messages().get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(((ToolCallResultMessage) input.messages().get(0)).results().getFirst().content())
        .containsExactly(
            textContent("loaded"),
            textContent(
                "The content of known.txt ("
                    + idOf(known).value()
                    + ") follows in the next message."));
    assertThat(input.messages().get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            message -> {
              assertThat(message.metadata())
                  .isEqualTo(TestDocuments.toolCallDocumentsMetadata(message));
              assertThat(message.content())
                  .containsExactly(
                      textContent(ToolCallResultsInputBuilder.DOCUMENT_CONTENT_PREAMBLE),
                      AgentDocumentContent.of(AgentDocumentId.of(known))
                          .withDelivery(DocumentDelivery.CONTENT));
            });
    assertThat(input.documents()).isEmpty();
  }

  @Test
  void aResultWithoutContentOfItsOwnEndsWithTheSentenceOnly() {
    final var entry =
        runtimeEntry(
            "c1", "alpha", RuntimeToolResult.success(List.of()).withDocumentContent(idOf(known)));

    final var input = builder.build(knowing(known), batchOf(entry), Set.of());

    assertThat(((ToolCallResultMessage) input.messages().get(0)).results().getFirst().content())
        .containsExactly(followsSentence(known));
  }

  @Test
  void aDocumentWithoutAFileNameIsNamedByItsIdAlone() {
    final var nameless =
        new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
            .create(
                DocumentCreationRequest.from("nameless".getBytes(StandardCharsets.UTF_8))
                    .contentType("text/plain")
                    .build());
    final var entry =
        runtimeEntry(
            "c1",
            "alpha",
            RuntimeToolResult.success(List.of()).withDocumentContent(idOf(nameless)));

    final var input = builder.build(knowing(nameless), batchOf(entry), Set.of());

    assertThat(((ToolCallResultMessage) input.messages().get(0)).results().getFirst().content())
        .containsExactly(
            textContent(
                "The content of " + idOf(nameless).value() + " follows in the next message."));
  }

  @Test
  void anErrorResultAsksForNoContent() {
    final var entry = runtimeEntry("c1", "alpha", RuntimeToolResult.error("no"));

    final var input = builder.build(knowing(known), batchOf(entry), Set.of());

    assertThat(entry.effects()).isEmpty();
    assertThat(input.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
  }

  @Test
  void aDocumentWhoseContentTheModelReadsIsNotAddedAgainAndTheResultSaysSo() {
    final var entry =
        runtimeEntry(
            "c1", "alpha", RuntimeToolResult.success("loaded").withDocumentContent(idOf(known)));

    final var input = builder.build(knowing(known), batchOf(entry), Set.of(idOf(known)));

    assertThat(input.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    assertThat(((ToolCallResultMessage) input.messages().getFirst()).results().getFirst().content())
        .containsExactly(
            textContent("loaded"),
            textContent(
                "The content of known.txt ("
                    + idOf(known).value()
                    + ") is already in the conversation."));
  }

  @Test
  void aDocumentThatSeveralCallsAskForIsAddedOnceAndTheLaterCallsSayItIsInTheConversation() {
    final var other = createDocument("other", "other.txt");
    final var batch =
        batchOf(
            runtimeEntry(
                "c1",
                "alpha",
                RuntimeToolResult.success("a")
                    .withDocumentContent(idOf(other))
                    .withDocumentContent(idOf(known))),
            runtimeEntry(
                "c2", "alpha", RuntimeToolResult.success("b").withDocumentContent(idOf(known))));

    final var input = builder.build(knowing(known, other), batch, Set.of());

    assertThat(((UserMessage) input.messages().get(1)).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilder.DOCUMENT_CONTENT_PREAMBLE),
            AgentDocumentContent.of(AgentDocumentId.of(other))
                .withDelivery(DocumentDelivery.CONTENT),
            AgentDocumentContent.of(AgentDocumentId.of(known))
                .withDelivery(DocumentDelivery.CONTENT));
    final var results = ((ToolCallResultMessage) input.messages().get(0)).results();
    assertThat(results.get(0).content())
        .containsExactly(textContent("a"), followsSentence(other), followsSentence(known));
    assertThat(results.get(1).content()).containsExactly(textContent("b"), alreadySentence(known));
  }

  @Test
  void aDocumentThatACallAsksForTwiceGetsOneSentence() {
    final var entry =
        runtimeEntry(
            "c1",
            "alpha",
            RuntimeToolResult.success("a")
                .withDocumentContent(idOf(known))
                .withDocumentContent(idOf(known)));

    final var input = builder.build(knowing(known), batchOf(entry), Set.of());

    assertThat(((ToolCallResultMessage) input.messages().get(0)).results().getFirst().content())
        .containsExactly(textContent("a"), followsSentence(known));
  }

  @Test
  void aDocumentTheResultReturnsItselfCanBeNamedByTheSameResult() {
    final var entry =
        runtimeEntry(
            "c1",
            "alpha",
            RuntimeToolResult.success(List.of(DocumentContent.documentContent(first)))
                .withDocumentContent(idOf(first)));

    final var input = builder.build(WITH_DOCUMENTS, batchOf(entry), Set.of());

    assertThat(input.documents())
        .containsExactly(AgentDocument.of(first, AgentDocumentOrigin.toolCall("alpha", "c1")));
    assertThat(((ToolCallResultMessage) input.messages().get(0)).results().getFirst().content())
        .containsExactly(AgentDocumentContent.of(idOf(first)), followsSentence(first));
    assertThat(((UserMessage) input.messages().get(1)).content())
        .last()
        .isEqualTo(
            AgentDocumentContent.of(AgentDocumentId.of(first))
                .withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void aResultOfAProcessToolThatCarriesAnEffectGetsTheSentenceAsWell() {
    final var processEntry =
        entryWithEffects(
            result("c1", "process_tool", textContent("done")),
            List.of(new ToolCallEffect.AddDocumentContent(idOf(known))));

    final var input = builder.build(knowing(known), batchOf(processEntry), Set.of());

    assertThat(((ToolCallResultMessage) input.messages().get(0)).results().getFirst().content())
        .containsExactly(textContent("done"), followsSentence(known));
    assertThat(((UserMessage) input.messages().get(1)).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilder.DOCUMENT_CONTENT_PREAMBLE),
            AgentDocumentContent.of(idOf(known)).withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void aCallNeverSeesTheDocumentsAnotherCallReturned() {
    final var returning =
        runtimeEntry(
            "c1",
            "alpha",
            RuntimeToolResult.success(List.of(DocumentContent.documentContent(first))));
    final var naming =
        runtimeEntry("c2", "beta", RuntimeToolResult.success("a").withDocumentContent(idOf(first)));

    assertThatThrownBy(() -> builder.build(WITH_DOCUMENTS, batchOf(returning, naming), Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The runtime tool 'beta' refers to the document '%s', which the agent does not know and the result does not return"
                .formatted(idOf(first)));
  }

  @Test
  void aDocumentTheAgentDoesNotKnowIsADefectOfTheTool() {
    final var entry =
        runtimeEntry(
            "c1",
            "alpha",
            RuntimeToolResult.success("a").withDocumentContent(AgentDocumentId.of("unknown")));

    assertThatThrownBy(() -> builder.build(WITH_DOCUMENTS, batchOf(entry), Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The runtime tool 'alpha' refers to the document '%s', which the agent does not know and the result does not return"
                .formatted(AgentDocumentId.of("unknown")));
  }

  @Test
  void aResultOfAProcessToolThatNamesAnUnknownDocumentIsADefectToo() {
    final var processEntry =
        entryWithEffects(
            result("c1", "process_tool", textContent("done")),
            List.of(new ToolCallEffect.AddDocumentContent(idOf(known))));

    assertThatThrownBy(() -> builder.build(WITH_DOCUMENTS, batchOf(processEntry), Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The runtime tool 'process_tool' refers to the document '%s', which the agent does not know and the result does not return"
                .formatted(idOf(known)));
  }

  @Test
  void aResultOfAnAgentThatDoesNotTrackItsDocumentsThatAsksForContentIsADefectOfTheTool() {
    final var entry =
        runtimeEntry(
            "c1",
            "alpha",
            RuntimeToolResult.success(List.of(DocumentContent.documentContent(first)))
                .withDocumentContent(idOf(first)));

    assertThatThrownBy(() -> builder.build(WITHOUT_DOCUMENTS, batchOf(entry), Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The runtime tool 'alpha' refers to a document by its id, which an agent instance that does not track its documents does not support");
  }

  /** An entry with the documents the generic walker finds and the given effects. */
  private static ToolCallBatch.Entry entryWithEffects(
      ToolCallResultContent result, List<ToolCallEffect> effects) {
    return new ToolCallBatch.Entry(
        result, ToolCallResultDocumentExtractor.documentsOf(result.content()), effects);
  }
}
