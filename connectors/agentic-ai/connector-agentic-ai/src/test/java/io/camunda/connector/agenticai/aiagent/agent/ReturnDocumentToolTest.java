/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

package io.camunda.connector.agenticai.aiagent.agent;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.localtool.LocalToolRegistryImpl;
import io.camunda.connector.agenticai.aiagent.localtool.ReturnDocumentTool;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.document.SelectedDocument;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

/** The {@code return_document} tool run by the framework, the way the agent loop runs it. */
class ReturnDocumentToolTest {

  private final LocalToolCallHandler handler =
      new LocalToolCallHandlerImpl(
          new LocalToolRegistryImpl(List.of(new ReturnDocumentTool())),
          TestObjectMapperSupplier.INSTANCE);

  private final Document report = createDocument("report", "report.txt");
  private final Document summary = createDocument("summary", "summary.txt");
  private final String reportId = DocumentHandle.idFor(report);
  private final String summaryId = DocumentHandle.idFor(summary);

  private static String tag(String id) {
    return "<doc id=\"" + id + "\" />";
  }

  private final DocumentRegistry registry =
      DocumentRegistry.empty()
          .withDocuments(
              List.of(
                  new IngestedDocument(report, DocumentSource.toolCall("search", "call-1", null)),
                  new IngestedDocument(summary, DocumentSource.userMessage(null))));

  private static final AgentConfiguration ENABLED =
      new AgentConfiguration(
          TEST_CHAT_MODEL,
          TEST_SYSTEM_PROMPT,
          null,
          null,
          null,
          null,
          new AgentTaskResponseConfiguration(null, null, true));
  private static final AgentConfiguration DISABLED =
      new AgentConfiguration(
          TEST_CHAT_MODEL,
          TEST_SYSTEM_PROMPT,
          null,
          null,
          null,
          null,
          new AgentTaskResponseConfiguration(null, null, false));

  @Test
  void definitionDescribesOneDocumentPerCallUnderAKey() {
    final var definition = new ReturnDocumentTool().definition();

    assertThat(definition.description())
        .isEqualTo(
            "Attaches one document to your final response. Use this only for a document the user"
                + " should receive in your answer; do not use it for documents you only pass to"
                + " other tools. Call it once per document. The key is a stable identifier the"
                + " process uses to find the document; it starts with a letter or an underscore and"
                + " continues with letters, digits, underscores or hyphens. The description is"
                + " optional. Calling it again with the same key replaces the document returned"
                + " under that key.");
    assertThat(definition.inputSchema().get("required")).isEqualTo(List.of("key", "document"));
    assertThat(definition.inputSchema().get("properties"))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsOnlyKeys("key", "document", "description");
    assertThat(definition.inputSchema().toString()).contains("format=camunda-document");
  }

  @Test
  void isOfferedOnlyWhileTheResponseConfigurationOptsIn() {
    assertThat(handler.availableToolDefinitions(ENABLED))
        .containsExactly(new ReturnDocumentTool().definition());
    assertThat(handler.availableToolDefinitions(DISABLED)).isEmpty();
  }

  @Test
  void answersAValidCallAndSelectsTheDocumentUnderItsKey() {
    final var split =
        run(
            ENABLED,
            call(
                "1", Map.of("key", "summary", "document", tag(summaryId), "description", "Short")));

    assertThat(split.localResults())
        .extracting(ToolCallResult::content)
        .containsExactly("Returned the document under the key 'summary'.");
    assertThat(split.conversation().returnDocuments())
        .containsExactly(Map.entry("summary", new SelectedDocument(summaryId, "Short")));
  }

  @Test
  void theDescriptionIsOptional() {
    final var split = run(ENABLED, call("1", Map.of("key", "report", "document", tag(reportId))));

    assertThat(split.conversation().returnDocuments())
        .containsExactly(Map.entry("report", new SelectedDocument(reportId, null)));
  }

  @Test
  void keepsTheCallOrderOfTheKeysOverTheCallsOfABatch() {
    final var split =
        run(
            ENABLED,
            call("1", Map.of("key", "b", "document", tag(summaryId))),
            call("2", Map.of("key", "a", "document", tag(reportId))));

    assertThat(split.conversation().returnDocuments().keySet()).containsExactly("b", "a");
  }

  @Test
  void aKeyThatIsAlreadySelectedIsReplacedInPlace() {
    final var split =
        run(
            ENABLED,
            call("1", Map.of("key", "first", "document", tag(reportId))),
            call("2", Map.of("key", "second", "document", tag(summaryId))),
            call(
                "3",
                Map.of("key", "first", "document", tag(summaryId), "description", "Replaced")));

    assertThat(split.conversation().returnDocuments())
        .containsExactly(
            Map.entry("first", new SelectedDocument(summaryId, "Replaced")),
            Map.entry("second", new SelectedDocument(summaryId, null)));
  }

  @Test
  void answersAnUnknownIdWithAnErrorListingTheKnownDocumentsAndSelectsNothing() {
    final var split = run(ENABLED, call("1", Map.of("key", "k", "document", tag("bogus"))));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content().toString())
                    .startsWith(
                        "Unknown document id 'bogus' for parameter 'document'. Known documents:")
                    .contains(reportId, summaryId));
    assertThat(split.conversation().returnDocuments()).isEmpty();
  }

  @Test
  void rejectsABareIdThatIsNotATag() {
    final var split = run(ENABLED, call("1", Map.of("key", "k", "document", reportId)));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result -> assertThat(result.content().toString()).contains("document", "<doc id="));
    assertThat(split.conversation().returnDocuments()).isEmpty();
  }

  @Test
  void answersAnInvalidKeyWithAnErrorAndSelectsNothing() {
    for (final var key : List.of("", "1abc", "a b", "a.b", "-a")) {
      final var split = run(ENABLED, call("1", Map.of("key", key, "document", tag(reportId))));

      assertThat(split.localResults())
          .singleElement()
          .satisfies(
              result ->
                  assertThat(result.content().toString())
                      .startsWith("Parameter 'key' must match [A-Za-z_][A-Za-z0-9_-]*"));
      assertThat(split.conversation().returnDocuments()).isEmpty();
    }
  }

  @Test
  void acceptsKeysWithUnderscoresDigitsAndHyphens() {
    final var split =
        run(ENABLED, call("1", Map.of("key", "_report-2024_final", "document", tag(reportId))));

    assertThat(split.conversation().returnDocuments()).containsOnlyKeys("_report-2024_final");
  }

  @Test
  void answersAMissingKeyOrDocumentWithAnError() {
    final var nullKey = new HashMap<String, Object>();
    nullKey.put("key", null);
    nullKey.put("document", tag(reportId));

    for (final var arguments :
        List.<Map<String, Object>>of(
            Map.of("document", tag(reportId)), nullKey, Map.of("key", "k"))) {
      final var split = run(ENABLED, call("1", arguments));

      assertThat(split.localResults()).singleElement();
      assertThat(split.localResults().get(0).content().toString()).contains("Parameter");
      assertThat(split.conversation().returnDocuments()).isEmpty();
    }
  }

  @Test
  void leavesTheCallToTheProcessWhenTheToolIsNotOffered() {
    final var call = call("1", Map.of("key", "k", "document", tag(reportId)));

    final var split = run(DISABLED, call);

    assertThat(split.hasLocalResults()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(call);
  }

  @Test
  void answersTheLocalCallsOfAMixedBatchAndLeavesTheRealCallToTheProcess() {
    final var split =
        run(
            ENABLED,
            call("r1", Map.of("key", "report", "document", tag(reportId))),
            call("r2", Map.of("key", "other", "document", tag("bogus"))),
            new ToolCall("t1", "getWeather", Map.of("location", "MUC")));

    assertThat(split.conversation().returnDocuments())
        .containsExactly(Map.entry("report", new SelectedDocument(reportId, null)));
    assertThat(split.localResults()).extracting(ToolCallResult::id).containsExactly("r1", "r2");
    assertThat(split.remainingCalls()).extracting(ToolCall::id).containsExactly("t1");
    assertThat(split.localResults().get(0).content())
        .isEqualTo("Returned the document under the key 'report'.");
    assertThat(split.localResults().get(1).content().toString())
        .startsWith("Unknown document id 'bogus' for parameter 'document'.");
  }

  private static ToolCall call(String id, Map<String, Object> arguments) {
    return new ToolCall(id, ReturnDocumentTool.NAME, arguments);
  }

  private LocalToolCallHandler.LocalToolCallSplit run(
      AgentConfiguration configuration, ToolCall... toolCalls) {
    final var conversation =
        AgentConversation.rehydrate(
                configuration,
                AgentContext.builder()
                    .state(AgentState.READY)
                    .documents(registry.entries())
                    .build(),
                TurnReconstructor.reconstruct(List.of()),
                null,
                List.of(userMessage("hi")))
            .ingest(assistantMessage("calling", List.of(toolCalls)), AgentMetrics.empty());
    return handler.handle(conversation, List.of(toolCalls));
  }

  private static Document createDocument(String content, String fileName) {
    return new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE)
        .create(
            DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
                .contentType("text/plain")
                .fileName(fileName)
                .build());
  }
}
