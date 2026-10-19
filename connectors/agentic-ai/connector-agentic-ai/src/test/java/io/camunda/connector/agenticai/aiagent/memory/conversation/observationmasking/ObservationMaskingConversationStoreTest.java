/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.observationmasking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.connector.agenticai.aiagent.agentinstance.AgentHistoryEvidence;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationLoadResult;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationStoreRequest;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ObservationMaskingConversationStoreTest {

  private final ObservationMaskingConversationStore store =
      new ObservationMaskingConversationStore(
          TestObjectMapperSupplier.INSTANCE, 0, "http://127.0.0.1");

  @AfterEach
  void closeStore() {
    store.close();
  }

  @Test
  void masksOlderResultsAndKeepsDocumentsAndAnalystFeedback() {
    final var document =
        new DocumentReferenceContent(
            "statement-1",
            "statement.pdf",
            "application/pdf",
            123L,
            "user_message",
            null,
            null,
            DocumentDelivery.CONTENT);
    final var messages = new ArrayList<Message>();
    messages.add(
        UserMessage.builder()
            .content(List.of(TextContent.textContent("Review the attached statement."), document))
            .build());
    for (int i = 1; i <= 4; i++) {
      messages.add(toolResult("call-" + i, "tool-" + i, "full-result-" + i));
    }
    messages.add(
        UserMessage.builder()
            .content(List.of(TextContent.textContent("latest analyst feedback")))
            .build());

    final var context = storeContext(messages);
    final ConversationLoadResult loaded =
        store
            .createSession(
                mock(AgentExecutionContext.class), AgentContext.empty().withConversation(context))
            .loadMessages(AgentContext.empty().withConversation(context));

    final var results =
        loaded.messages().stream()
            .filter(ToolCallResultMessage.class::isInstance)
            .map(ToolCallResultMessage.class::cast)
            .flatMap(message -> message.results().stream())
            .toList();
    assertThat(results).hasSize(4);
    assertThat(results.get(0).content().getFirst().toString())
        .contains("tool-1", "call-1", "/results?callId=call-1")
        .doesNotContain("full-result-1");
    assertThat(results.get(1).content().getFirst().toString())
        .contains("tool-2", "call-2")
        .doesNotContain("full-result-2");
    assertThat(results.get(2).content()).containsExactly(TextContent.textContent("full-result-3"));
    assertThat(results.get(3).content()).containsExactly(TextContent.textContent("full-result-4"));
    assertThat(loaded.messages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .isInstanceOfSatisfying(
                        UserMessage.class, user -> assertThat(user.content()).contains(document)));
    assertThat(loaded.messages())
        .anySatisfy(
            message ->
                assertThat(message)
                    .isInstanceOfSatisfying(
                        UserMessage.class,
                        user ->
                            assertThat(user.content())
                                .contains(TextContent.textContent("latest analyst feedback"))));
  }

  @Test
  void pinsLedgerAndServesSafeReadOnlyEvidence() throws Exception {
    final var messages =
        List.<Message>of(
            toolResult(
                "reconcile-1",
                "ReconcileTransactions",
                Map.of(
                    "matches",
                    List.of(Map.of("id", "txn-17"), Map.of("id", "txn-18")),
                    "overchargeAmount",
                    482.17,
                    "filingDeadline",
                    "2027-01-13",
                    "apiKey",
                    "never-return-this")),
            toolResult("fraud-1", "AskFraudRiskAgent", "{\"risk\":\"LOW\"}"),
            toolResult("recent-1", "recentTool", "recent full result"));

    final var context = storeContext(messages);
    assertThat(context.ledger())
        .isEqualTo(new CaseLedger(List.of("txn-17", "txn-18"), "482.17", "LOW", "2027-01-13"));
    assertThat(AgentHistoryEvidence.customMemory(AgentContext.empty().withConversation(context)))
        .containsEntry("storeUrl", context.storeUrl())
        .containsEntry("maskedToolResults", 1L);
    final var memoryPrompt =
        new ObservationMaskingSystemPromptContributor()
            .contribute(
                mock(AgentExecutionContext.class), AgentContext.empty().withConversation(context));
    assertThat(memoryPrompt)
        .contains("txn-17", "482.17", "LOW", "2027-01-13")
        .contains("Do not repeat completed tool calls");
    final var persisted =
        TestObjectMapperSupplier.INSTANCE.convertValue(
            TestObjectMapperSupplier.INSTANCE.valueToTree(
                AgentContext.empty().withConversation(context)),
            AgentContext.class);
    assertThat(persisted.conversation()).isEqualTo(context);

    final var client = HttpClient.newHttpClient();
    final var snapshotResponse =
        client.send(
            HttpRequest.newBuilder(URI.create(context.storeUrl())).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(snapshotResponse.statusCode()).isEqualTo(200);
    assertThat(snapshotResponse.body())
        .contains("matchedTransactionIds", "txn-17", "overchargeAmount", "482.17", "2027-01-13")
        .doesNotContain("never-return-this");

    final var resultResponse =
        client.send(
            HttpRequest.newBuilder(URI.create(context.storeUrl() + "/results?callId=reconcile-1"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(resultResponse.statusCode()).isEqualTo(200);
    assertThat(resultResponse.body())
        .contains("matches", "txn-17", "[REDACTED]")
        .doesNotContain("never-return-this");
    assertThat(
            client
                .send(
                    HttpRequest.newBuilder(URI.create(context.storeUrl()))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                    HttpResponse.BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(405);

    final var reloaded =
        store
            .createSession(
                mock(AgentExecutionContext.class), AgentContext.empty().withConversation(context))
            .loadMessages(AgentContext.empty().withConversation(context));
    assertThat(reloaded.messages()).isEqualTo(context.messages());
  }

  @Test
  void extractsLedgerFactsFromTextToolResults() {
    final var ledger =
        CaseLedger.fromMessages(
            List.of(
                toolResult(
                    "sandbox-1",
                    "sandbox_execute",
                    "overchargeAmount=482.17\nfilingDeadline=2027-01-13"),
                toolResult("fraud-1", "AskFraudRiskAgent", "Fraud risk opinion: LOW")));

    assertThat(ledger).isEqualTo(new CaseLedger(List.of(), "482.17", "LOW", "2027-01-13"));
  }

  private ObservationMaskingConversationContext storeContext(List<Message> messages) {
    try (final var session =
        store.createSession(mock(AgentExecutionContext.class), AgentContext.empty())) {
      return (ObservationMaskingConversationContext)
          session.storeMessages(AgentContext.empty(), ConversationStoreRequest.of(messages));
    }
  }

  private static ToolCallResultMessage toolResult(String id, String name, String text) {
    return toolResult(id, name, (Object) text);
  }

  private static ToolCallResultMessage toolResult(String id, String name, Object value) {
    final List<Content> content =
        value instanceof String text
            ? List.of((Content) TextContent.textContent(text))
            : List.of(ObjectContent.objectContent(value));
    return ToolCallResultMessage.builder()
        .results(
            List.of(ToolCallResultContent.builder().id(id).name(name).content(content).build()))
        .build();
  }
}
