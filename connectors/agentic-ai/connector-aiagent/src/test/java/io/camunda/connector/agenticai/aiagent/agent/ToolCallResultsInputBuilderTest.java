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

import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultsInputBuilder.Entry;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentLimits;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistries;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
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

  private final ToolCallResultsInputBuilder builder =
      new ToolCallResultsInputBuilderImpl(
          new DocumentContentGuard(DocumentContentLimits.defaults()),
          TestObjectMapperSupplier.INSTANCE);

  private final Document first = createDocument("first", "first.txt");
  private final Document nested = createDocument("nested", "nested.txt");
  private final Document third = createDocument("third", "third.txt");
  private final Document known = createDocument("known", "known.txt");
  private final Document archive = archive();

  private static Document archive() {
    return new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
        .create(
            DocumentCreationRequest.from("PK".getBytes(StandardCharsets.UTF_8))
                .contentType("application/zip")
                .fileName("archive.zip")
                .build());
  }

  private static ToolCallResultContent result(String id, String name, Content... content) {
    return ToolCallResultContent.builder()
        .id(id)
        .name(name)
        .content(List.of(content))
        .completedAt(COMPLETED_AT)
        .build();
  }

  private static DocumentId idOf(Document document) {
    return DocumentHandle.idFor(document);
  }

  private static AgentContext knowing(Document... documents) {
    return WITH_DOCUMENTS.withDocumentRegistry(
        DocumentRegistries.withDocuments(
            DocumentRegistry.empty(),
            Arrays.stream(documents)
                .map(document -> new IngestedDocument(document, DocumentSource.userMessage()))
                .toList()));
  }

  private static Entry runtimeEntry(String id, String name, RuntimeToolResult toolResult) {
    final var call = new ToolCall(id, name, Map.of()).withKind(ToolKind.RUNTIME);
    return Entry.of(new RuntimeToolExecution(call, toolResult, COMPLETED_AT, COMPLETED_AT));
  }

  private List<Entry> entries() {
    return List.of(
        new Entry(
            result("c1", "alpha", textContent("one"), DocumentContent.documentContent(first)),
            List.of(first)),
        new Entry(result("c2", "beta", textContent("two")), List.of()),
        new Entry(
            result("c3", "gamma", objectContent(Map.of("file", nested, "again", third))),
            List.of(nested, third)));
  }

  @Test
  void withTheDocumentsFeatureTheResultsAreOneMessageAndTheDocumentsAreReportedInCallOrder() {
    final var input = builder.build(WITH_DOCUMENTS, entries(), Set.of());

    assertThat(input.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    assertThat(input.documents())
        .containsExactly(
            new IngestedDocument(first, DocumentSource.toolCall("alpha", "c1")),
            new IngestedDocument(nested, DocumentSource.toolCall("gamma", "c3")),
            new IngestedDocument(third, DocumentSource.toolCall("gamma", "c3")));
  }

  @Test
  void withTheDocumentsFeatureADocumentThatIsAWholeResultIsItsTagAndANestedOneStaysADocument() {
    final var input = builder.build(WITH_DOCUMENTS, entries(), Set.of());

    final var results = ((ToolCallResultMessage) input.messages().getFirst()).results();
    assertThat(results.get(0).content())
        .containsExactly(textContent("one"), DocumentReferences.from(first));
    assertThat(results.get(1).content()).containsExactly(textContent("two"));
    assertThat(results.get(2).content())
        .containsExactly(objectContent(Map.of("file", nested, "again", third)));
  }

  @Test
  void withTheDocumentsFeatureAResultWithoutDocumentsReportsNone() {
    final var input =
        builder.build(
            WITH_DOCUMENTS,
            List.of(new Entry(result("c2", "beta", textContent("two")), List.of())),
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
                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              assertThat(message.content())
                  .first()
                  .isEqualTo(
                      textContent(ToolCallResultsInputBuilderImpl.TOOL_CALL_DOCUMENTS_PREAMBLE));
              assertThat(message.content())
                  .contains(
                      DocumentContent.documentContent(first),
                      DocumentContent.documentContent(nested),
                      DocumentContent.documentContent(third));
            });
    final var results = ((ToolCallResultMessage) input.messages().getFirst()).results();
    assertThat(results.get(0).content())
        .containsExactly(textContent("one"), DocumentContent.documentContent(first));
  }

  @Test
  void aSuccessfulResultThatNamesAKnownDocumentGetsItsContentInAMessageAfterTheResults() {
    final var entry =
        runtimeEntry(
            "c1", "alpha", RuntimeToolResult.success("loaded").withDocumentContent(idOf(known)));

    final var input = builder.build(knowing(known), List.of(entry), Set.of());

    assertThat(input.messages()).hasSize(2);
    assertThat(input.messages().get(0)).isInstanceOf(ToolCallResultMessage.class);
    assertThat(input.messages().get(1))
        .isInstanceOfSatisfying(
            UserMessage.class,
            message -> {
              assertThat(message.metadata())
                  .containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
              assertThat(message.content())
                  .containsExactly(
                      textContent(ToolCallResultsInputBuilderImpl.DOCUMENT_CONTENT_PREAMBLE),
                      DocumentReferences.from(known, DocumentSource.userMessage())
                          .withDelivery(DocumentDelivery.CONTENT));
            });
    assertThat(input.documents()).isEmpty();
  }

  @Test
  void anErrorResultHasNoEffects() {
    final var entry = runtimeEntry("c1", "alpha", RuntimeToolResult.error("no"));

    final var input = builder.build(knowing(known), List.of(entry), Set.of());

    assertThat(entry.documentContent()).isEmpty();
    assertThat(input.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
  }

  @Test
  void aDocumentWhoseContentTheModelReadsIsNotAddedAgainAndTheResultStaysAsItIs() {
    final var entry =
        runtimeEntry(
            "c1", "alpha", RuntimeToolResult.success("loaded").withDocumentContent(idOf(known)));

    final var input = builder.build(knowing(known), List.of(entry), Set.of(idOf(known)));

    assertThat(input.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    assertThat(((ToolCallResultMessage) input.messages().getFirst()).results().getFirst().content())
        .containsExactly(textContent("loaded"));
  }

  @Test
  void aDocumentThatSeveralCallsAskForIsAddedOnceInTheOrderOfTheFirstCall() {
    final var other = createDocument("other", "other.txt");
    final var entries =
        List.of(
            runtimeEntry(
                "c1",
                "alpha",
                RuntimeToolResult.success("a")
                    .withDocumentContent(idOf(other))
                    .withDocumentContent(idOf(known))),
            runtimeEntry(
                "c2", "alpha", RuntimeToolResult.success("b").withDocumentContent(idOf(known))));

    final var input = builder.build(knowing(known, other), entries, Set.of());

    assertThat(((UserMessage) input.messages().get(1)).content())
        .containsExactly(
            textContent(ToolCallResultsInputBuilderImpl.DOCUMENT_CONTENT_PREAMBLE),
            DocumentReferences.from(other, DocumentSource.userMessage())
                .withDelivery(DocumentDelivery.CONTENT),
            DocumentReferences.from(known, DocumentSource.userMessage())
                .withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void aDocumentTheResultReturnsItselfCanBeNamedByTheSameResult() {
    final var entry =
        runtimeEntry(
            "c1",
            "alpha",
            RuntimeToolResult.success(List.of(DocumentContent.documentContent(first)))
                .withDocumentContent(idOf(first)));

    final var input = builder.build(WITH_DOCUMENTS, List.of(entry), Set.of());

    assertThat(input.documents())
        .containsExactly(new IngestedDocument(first, DocumentSource.toolCall("alpha", "c1")));
    assertThat(((UserMessage) input.messages().get(1)).content())
        .last()
        .isEqualTo(
            DocumentReferences.from(first, DocumentSource.toolCall("alpha", "c1"))
                .withDelivery(DocumentDelivery.CONTENT));
  }

  @Test
  void aResultWhoseDocumentTheGuardRejectsBecomesAnErrorResultAndReportsNoDocument() {
    final var rejected =
        runtimeEntry(
            "c1",
            "alpha",
            RuntimeToolResult.success(List.of(DocumentContent.documentContent(first)))
                .withDocumentContent(idOf(archive)));
    final var other =
        runtimeEntry("c2", "beta", RuntimeToolResult.success(List.of(textContent("fine"))));

    final var input = builder.build(knowing(archive), List.of(rejected, other), Set.of());

    assertThat(input.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
    final var results = ((ToolCallResultMessage) input.messages().getFirst()).results();
    assertThat(results.get(0).id()).isEqualTo("c1");
    assertThat(results.get(0).completedAt()).isEqualTo(COMPLETED_AT);
    assertThat(results.get(0).content())
        .singleElement()
        .isEqualTo(
            textContent(
                "The document is of type application/zip which cannot be loaded into the conversation; pass its tag to a tool that processes it."));
    assertThat(results.get(1).content()).containsExactly(textContent("fine"));
    assertThat(input.documents()).isEmpty();
  }

  @Test
  void aRejectedResultAddsNoContentOfTheDocumentsItNamedBeforeTheRejectedOne() {
    final var entry =
        runtimeEntry(
            "c1",
            "alpha",
            RuntimeToolResult.success("loaded")
                .withDocumentContent(idOf(known))
                .withDocumentContent(idOf(archive)));

    final var input = builder.build(knowing(known, archive), List.of(entry), Set.of());

    assertThat(input.messages()).singleElement().isInstanceOf(ToolCallResultMessage.class);
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

    assertThatThrownBy(() -> builder.build(WITH_DOCUMENTS, List.of(returning, naming), Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("beta")
        .hasMessageContaining("does not know");
  }

  @Test
  void aDocumentTheAgentDoesNotKnowIsADefectOfTheTool() {
    final var entry =
        runtimeEntry(
            "c1",
            "alpha",
            RuntimeToolResult.success("a").withDocumentContent(DocumentId.of("unknown")));

    assertThatThrownBy(() -> builder.build(WITH_DOCUMENTS, List.of(entry), Set.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("alpha")
        .hasMessageContaining("unknown");
  }

  @Test
  void withoutTheDocumentsFeatureTheAgentKnowsNoDocument() {
    final var entry =
        runtimeEntry(
            "c1", "alpha", RuntimeToolResult.success("a").withDocumentContent(idOf(known)));

    assertThatThrownBy(() -> builder.build(WITHOUT_DOCUMENTS, List.of(entry), Set.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
