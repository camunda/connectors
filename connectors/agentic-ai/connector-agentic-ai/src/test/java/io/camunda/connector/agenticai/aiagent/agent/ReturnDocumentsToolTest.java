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
import io.camunda.connector.agenticai.aiagent.localtool.ReturnDocumentsTool;
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
import org.junit.jupiter.api.Test;

/** The {@code return_documents} tool run by the framework, the way the agent loop runs it. */
class ReturnDocumentsToolTest {

  private final LocalToolCallHandler handler =
      new LocalToolCallHandlerImpl(
          new LocalToolRegistryImpl(List.of(new ReturnDocumentsTool())),
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
  void definitionRestrictsTheToolToDocumentsTheUserShouldReceive() {
    final var definition = new ReturnDocumentsTool().definition();

    assertThat(definition.description())
        .isEqualTo(
            "Attaches documents to your final response. Use this only for documents the user"
                + " should receive in your answer; do not use it for documents you only pass to"
                + " other tools. Pass the <doc id=\"...\" /> tags; the documents accumulate over"
                + " several calls.");
    assertThat(definition.inputSchema().toString()).contains("format=camunda-document");
  }

  @Test
  void isOfferedOnlyWhileTheResponseConfigurationOptsIn() {
    assertThat(handler.availableToolDefinitions(ENABLED))
        .containsExactly(new ReturnDocumentsTool().definition());
    assertThat(handler.availableToolDefinitions(DISABLED)).isEmpty();
  }

  @Test
  void answersAValidCallWithTheSelectionAndSelectsTheDocuments() {
    final var split =
        run(ENABLED, call("1", Map.of("ids", List.of(tag(summaryId), tag(reportId)))));

    assertThat(split.localResults())
        .extracting(ToolCallResult::content)
        .containsExactly("Selected 2 document(s) to return: %s, %s".formatted(summaryId, reportId));
    assertThat(split.conversation().returnDocumentIds()).containsExactly(summaryId, reportId);
  }

  @Test
  void dropsDuplicateIdsOfOneCall() {
    final var split = run(ENABLED, call("1", Map.of("ids", List.of(tag(reportId), tag(reportId)))));

    assertThat(split.localResults())
        .extracting(ToolCallResult::content)
        .containsExactly("Selected 1 document(s) to return: " + reportId);
    assertThat(split.conversation().returnDocumentIds()).containsExactly(reportId);
  }

  @Test
  void accumulatesTheSelectionOverTheCallsOfABatchAsAnOrderedUnion() {
    final var split =
        run(
            ENABLED,
            call("1", Map.of("ids", List.of(tag(summaryId)))),
            call("2", Map.of("ids", List.of(tag(reportId), tag(summaryId)))));

    assertThat(split.conversation().returnDocumentIds()).containsExactly(summaryId, reportId);
  }

  @Test
  void answersAnUnknownIdWithAnErrorListingTheKnownDocumentsAndSelectsNothing() {
    final var split = run(ENABLED, call("1", Map.of("ids", List.of(tag(reportId), tag("bogus")))));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content().toString())
                    .startsWith(
                        "Unknown document id 'bogus' for parameter 'ids[1]'. Known documents:")
                    .contains(reportId, summaryId));
    assertThat(split.conversation().returnDocumentIds()).isEmpty();
  }

  @Test
  void answersAMissingOrEmptyIdsParameterWithAnError() {
    final var withNull = new HashMap<String, Object>();
    withNull.put("ids", null);

    for (final var arguments :
        List.<Map<String, Object>>of(Map.of(), withNull, Map.of("ids", List.of()))) {
      final var split = run(ENABLED, call("1", arguments));

      assertThat(split.localResults())
          .singleElement()
          .satisfies(
              result ->
                  assertThat(result.content().toString())
                      .startsWith(
                          "Parameter 'ids' must be a non-empty array of <doc id=\"...\" /> tags."));
      assertThat(split.conversation().returnDocumentIds()).isEmpty();
    }
  }

  @Test
  void answersANonArrayIdsParameterWithAnError() {
    final var split = run(ENABLED, call("1", Map.of("ids", tag(reportId))));

    assertThat(split.localResults())
        .singleElement()
        .satisfies(
            result ->
                assertThat(result.content().toString())
                    .startsWith("Parameter 'ids' must be an array."));
  }

  @Test
  void leavesTheCallToTheProcessWhenTheToolIsNotOffered() {
    final var call = call("1", Map.of("ids", List.of(tag(reportId))));

    final var split = run(DISABLED, call);

    assertThat(split.hasLocalResults()).isFalse();
    assertThat(split.remainingCalls()).containsExactly(call);
  }

  @Test
  void answersTheLocalCallsOfAMixedBatchAndLeavesTheRealCallToTheProcess() {
    final var split =
        run(
            ENABLED,
            call("r1", Map.of("ids", List.of(tag(reportId)))),
            call("r2", Map.of("ids", List.of(tag("bogus")))),
            new ToolCall("t1", "getWeather", Map.of("location", "MUC")));

    assertThat(split.conversation().returnDocumentIds()).containsExactly(reportId);
    assertThat(split.localResults()).extracting(ToolCallResult::id).containsExactly("r1", "r2");
    assertThat(split.remainingCalls()).extracting(ToolCall::id).containsExactly("t1");
    assertThat(split.localResults().get(0).content())
        .isEqualTo("Selected 1 document(s) to return: " + reportId);
    assertThat(split.localResults().get(1).content().toString())
        .startsWith("Unknown document id 'bogus' for parameter 'ids[0]'.");
  }

  private static ToolCall call(String id, Map<String, Object> arguments) {
    return new ToolCall(id, ReturnDocumentsTool.NAME, arguments);
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
