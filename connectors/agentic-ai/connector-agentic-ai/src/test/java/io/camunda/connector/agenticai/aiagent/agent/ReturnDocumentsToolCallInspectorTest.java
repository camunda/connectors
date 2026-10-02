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

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentConversation;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.model.TurnReconstructor;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.document.ReturnDocumentsTool;
import io.camunda.connector.agenticai.aiagent.model.request.AgentTaskResponseConfiguration;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReturnDocumentsToolCallInspectorTest {

  private final ReturnDocumentsToolCallInspector inspector = new ReturnDocumentsToolCallInspector();

  private final Document report = createDocument("report", "report.txt");
  private final Document summary = createDocument("summary", "summary.txt");
  private final String reportId = DocumentHandle.idFor(report);
  private final String summaryId = DocumentHandle.idFor(summary);

  private final DocumentRegistry registry =
      DocumentRegistry.empty()
          .withDocuments(
              List.of(
                  new IngestedDocument(report, "call-1", "search"),
                  new IngestedDocument(summary, null, null)));

  private final LocalToolCallContext enabled = new LocalToolCallContext(List.of(), registry, true);

  @Test
  void answersAValidCallWithTheSelectionAndReportsTheIds() {
    final var verdict = inspect(Map.of("ids", List.of(summaryId, reportId)), enabled);

    assertThat(verdict)
        .isEqualTo(
            new ToolCallVerdict.Answer(
                "Selected 2 document(s) to return: %s, %s".formatted(summaryId, reportId),
                List.of(summaryId, reportId)));
  }

  @Test
  void dropsDuplicateIdsOfOneCall() {
    final var verdict = inspect(Map.of("ids", List.of(reportId, reportId)), enabled);

    assertThat(verdict)
        .isEqualTo(
            new ToolCallVerdict.Answer(
                "Selected 1 document(s) to return: " + reportId, List.of(reportId)));
  }

  @Test
  void answersAnUnknownIdWithAnErrorListingTheKnownDocumentsAndSelectsNothing() {
    final var verdict = inspect(Map.of("ids", List.of(reportId, "bogus")), enabled);

    assertThat(verdict)
        .isInstanceOfSatisfying(
            ToolCallVerdict.Answer.class,
            answer -> {
              assertThat(answer.returnDocumentIds()).isEmpty();
              assertThat(answer.content().toString())
                  .startsWith(
                      "Unknown document id 'bogus' for parameter 'ids[1]'. Known documents:")
                  .contains(reportId, summaryId);
            });
  }

  @Test
  void answersAMissingOrEmptyIdsParameterWithAnError() {
    final var withNull = new HashMap<String, Object>();
    withNull.put("ids", null);

    for (final var arguments :
        List.<Map<String, Object>>of(Map.of(), withNull, Map.of("ids", List.of()))) {
      assertThat(inspect(arguments, enabled))
          .isInstanceOfSatisfying(
              ToolCallVerdict.Answer.class,
              answer -> {
                assertThat(answer.returnDocumentIds()).isEmpty();
                assertThat(answer.content().toString())
                    .startsWith("Parameter 'ids' must be a non-empty array of document ids.");
              });
    }
  }

  @Test
  void answersANonArrayIdsParameterWithAnError() {
    assertThat(inspect(Map.of("ids", reportId), enabled))
        .isInstanceOfSatisfying(
            ToolCallVerdict.Answer.class,
            answer ->
                assertThat(answer.content().toString())
                    .startsWith("Parameter 'ids' must be an array."));
  }

  @Test
  void passesWhenTheToolIsNotOfferedOrTheCallIsForAnotherTool() {
    final var disabled = new LocalToolCallContext(List.of(), registry, false);

    assertThat(inspect(Map.of("ids", List.of(reportId)), disabled))
        .isEqualTo(new ToolCallVerdict.Pass());
    assertThat(
            inspector.inspect(
                new ToolCall("1", "other", Map.of("ids", List.of(reportId))), enabled))
        .isEqualTo(new ToolCallVerdict.Pass());
  }

  @Test
  void answersTheWholeMixedBatchAndSelectsOnlyTheValidReturnCall() {
    final var handler =
        new LocalToolCallHandlerImpl(List.of(new DocumentParameterToolCallInspector(), inspector));
    final var toolCalls =
        List.of(
            new ToolCall("r1", ReturnDocumentsTool.NAME, Map.of("ids", List.of(reportId))),
            new ToolCall("r2", ReturnDocumentsTool.NAME, Map.of("ids", List.of("bogus"))),
            new ToolCall("t1", "getWeather", Map.of("location", "MUC")));
    final var conversation =
        AgentConversation.rehydrate(
                new AgentConfiguration(
                    TEST_CHAT_MODEL,
                    TEST_SYSTEM_PROMPT,
                    null,
                    null,
                    null,
                    null,
                    new AgentTaskResponseConfiguration(null, null, true)),
                AgentContext.builder()
                    .state(AgentState.READY)
                    .documents(registry.entries())
                    .build(),
                TurnReconstructor.reconstruct(List.of()),
                null,
                List.of(userMessage("hi")))
            .ingest(assistantMessage("calling", toolCalls), AgentMetrics.empty());

    final var answers = handler.handle(conversation, toolCalls).orElseThrow();

    assertThat(answers.returnDocumentIds()).containsExactly(reportId);
    assertThat(answers.results()).extracting(ToolCallResult::id).containsExactly("r1", "r2", "t1");
    assertThat(answers.results().get(0).content())
        .isEqualTo("Selected 1 document(s) to return: " + reportId);
    assertThat(answers.results().get(1).content().toString())
        .startsWith("Unknown document id 'bogus' for parameter 'ids[0]'.");
    assertThat(answers.results().get(2).content()).isEqualTo(LocalToolCallHandlerImpl.NOT_EXECUTED);
  }

  private ToolCallVerdict inspect(Map<String, Object> arguments, LocalToolCallContext context) {
    return inspector.inspect(new ToolCall("1", ReturnDocumentsTool.NAME, arguments), context);
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
