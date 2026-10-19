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
import java.util.Objects;
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
    final var compactionEvent = Objects.requireNonNull(context.compactionEvent());
    assertThat(compactionEvent.savedTokens()).isNull();
    assertThat(compactionEvent.message())
        .isEqualTo("Masked older tool results from tools: tool-1, tool-2.");
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

    try (final var session =
        store.createSession(
            mock(AgentExecutionContext.class), AgentContext.empty().withConversation(context))) {
      final var nextSnapshot =
          (ObservationMaskingConversationContext)
              session.storeMessages(
                  AgentContext.empty().withConversation(context),
                  ConversationStoreRequest.of(context.messages()));
      assertThat(nextSnapshot.compactionEvent()).isNull();
    }
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
  void retainsNestedFraudAnswerAndReconciliationFactsAfterMasking() throws Exception {
    final var messages =
        List.<Message>of(
            toolResult(
                "reconcile-1",
                "ReconcileTransactions",
                Map.of(
                    "matches",
                    List.of(Map.of("id", "TX-0911"), Map.of("id", "TX-0912")),
                    "overchargeAmount",
                    482.17,
                    "refundTotal",
                    34.99,
                    "refundsPosted",
                    List.of(Map.of("id", "TX-0914", "date", "2026-09-19", "amount", -34.99)),
                    "matchCount",
                    2,
                    "daysBetweenCharges",
                    1)),
            toolResult(
                "fraud-1", "AskFraudRiskAgent", Map.of("answer", "RISK: LOW\nCONFIDENCE: 0.95")),
            toolResult("recent-1", "sandbox_execute", "filingDeadline=2027-01-13"),
            toolResult("recent-2", "recentTool", "latest result"));
    final var context = storeContext(messages);
    assertThat(context.ledger().fraudVerdict()).isEqualTo("LOW");
    assertThat(context.ledger().matchedTransactionIds()).containsExactly("TX-0911", "TX-0912");
    assertThat(context.ledger().overchargeAmount()).isEqualTo("482.17");
    assertThat(context.ledger().reconciliationFacts())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "refundTotal", "34.99",
                "postedRefundTransactionIds", "TX-0914",
                "matchCount", "2",
                "daysBetweenCharges", "1"));
    final var prompt =
        new ObservationMaskingSystemPromptContributor()
            .contribute(
                mock(AgentExecutionContext.class), AgentContext.empty().withConversation(context));
    assertThat(prompt)
        .contains(
            "refundTotal: 34.99",
            "matchCount: 2",
            "daysBetweenCharges: 1",
            "fraud verdict: LOW",
            "postedRefundTransactionIds: TX-0914",
            "refunds already posted");
    assertThat(context.messages().getFirst().toString()).doesNotContain("34.99");
    final var persisted =
        TestObjectMapperSupplier.INSTANCE.convertValue(
            TestObjectMapperSupplier.INSTANCE.valueToTree(
                AgentContext.empty().withConversation(context)),
            AgentContext.class);
    assertThat(persisted.conversation()).isEqualTo(context);
    try (final var session = store.createSession(mock(AgentExecutionContext.class), persisted)) {
      final var revised =
          (ObservationMaskingConversationContext)
              session.storeMessages(persisted, ConversationStoreRequest.of(context.messages()));
      assertThat(revised.ledger()).isEqualTo(context.ledger());
      final var response =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(URI.create(revised.storeUrl())).GET().build(),
                  HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body())
          .contains("\"refundTotal\":\"34.99\"", "TX-0914", "\"fraudVerdict\":\"LOW\"");
    }
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
