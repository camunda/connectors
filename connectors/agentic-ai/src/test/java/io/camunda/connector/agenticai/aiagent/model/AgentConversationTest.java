/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.assistantMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.systemMessage;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.userMessage;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.memory.runtime.SlidingMessagesWindow;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.agenticai.model.message.SystemMessage;
import io.camunda.connector.agenticai.model.message.content.TextContent;
import io.camunda.connector.agenticai.model.tool.ToolCall;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentConversationTest {

  private static final AgentContext EMPTY_CONTEXT = AgentContext.empty();

  @Test
  void startCreatesConversationWithEmptyAddedMessages() {
    var conversation =
        AgentConversation.start(EMPTY_CONTEXT, List.of(userMessage("Hello")), List.of());

    assertThat(conversation.messages()).hasSize(1);
    assertThat(conversation.addedMessages()).isEmpty();
    assertThat(conversation.engineToolCallResults()).isEmpty();
  }

  // --- System-message upsert (ported from DefaultRuntimeMemoryTest) ---

  @Test
  void replacesExistingSystemMessageWhenDifferent() {
    var original = systemMessage("Original system");
    var newSys = systemMessage("New system message");
    var conversation =
        AgentConversation.start(EMPTY_CONTEXT, List.of(original, userMessage("Hello")), List.of());

    var updated = conversation.withTurn(newSys, List.of());

    assertThat(updated.messages()).hasSize(2); // no new message added
    assertThat(updated.messages().getFirst()).isEqualTo(newSys);
    assertThat(updated.messages()).filteredOn(m -> m instanceof SystemMessage).hasSize(1);
  }

  @Test
  void doesNotReplaceIdenticalSystemMessage() {
    var original = systemMessage("Same system");
    var identicalNewSys = systemMessage("Same system"); // equal but not same instance
    var conversation =
        AgentConversation.start(EMPTY_CONTEXT, List.of(original, userMessage("Hello")), List.of());

    var updated = conversation.withTurn(identicalNewSys, List.of());

    // original instance must be preserved (isSameAs)
    assertThat(updated.messages().getFirst()).isSameAs(original);
  }

  @Test
  void addsSystemMessageAtFrontWhenNoneExists() {
    var sys = systemMessage("New system");
    var conversation =
        AgentConversation.start(EMPTY_CONTEXT, List.of(userMessage("Hello")), List.of());

    var updated = conversation.withTurn(sys, List.of());

    assertThat(updated.messages()).filteredOn(m -> m instanceof SystemMessage).hasSize(1);
    assertThat(updated.messages().getFirst()).isEqualTo(sys);
  }

  @Test
  void nullSystemMessageLeavesMessagesUnchanged() {
    var conversation =
        AgentConversation.start(EMPTY_CONTEXT, List.of(userMessage("Hello")), List.of());

    var updated = conversation.withTurn(null, List.of());

    assertThat(updated.messages()).isEqualTo(conversation.messages());
  }

  @Test
  void withTurnFreezesAddedMessagesExcludingSystemMessage() {
    var sys = systemMessage("System");
    var user = userMessage("Hello");
    var conversation = AgentConversation.start(EMPTY_CONTEXT, List.of(), List.of());

    var updated = conversation.withTurn(sys, List.of(user));

    assertThat(updated.addedMessages()).containsExactly(user);
    assertThat(updated.addedMessages()).noneMatch(m -> m instanceof SystemMessage);
  }

  // --- ingest metric folding ---

  @Test
  void ingestIncrementsModelCallsAndTokenUsage() {
    var conversation = AgentConversation.start(EMPTY_CONTEXT, List.of(), List.of());
    var assistant = assistantMessage("Response");
    var tokenUsage =
        AgentMetrics.TokenUsage.builder().inputTokenCount(10).outputTokenCount(5).build();

    var after = conversation.ingest(assistant, tokenUsage);

    assertThat(after.context().metrics().modelCalls()).isEqualTo(1);
    assertThat(after.context().metrics().tokenUsage().inputTokenCount()).isEqualTo(10);
    assertThat(after.context().metrics().tokenUsage().outputTokenCount()).isEqualTo(5);
    assertThat(after.context().metrics().toolCalls()).isEqualTo(0);
  }

  @Test
  void ingestIncrementsToolCallsWhenAssistantHasToolCalls() {
    var conversation = AgentConversation.start(EMPTY_CONTEXT, List.of(), List.of());
    var assistant =
        assistantMessage(
            "Calling tools",
            List.of(
                ToolCall.builder().id("1").name("tool1").build(),
                ToolCall.builder().id("2").name("tool2").build()));
    var tokenUsage = AgentMetrics.TokenUsage.empty();

    var after = conversation.ingest(assistant, tokenUsage);

    assertThat(after.context().metrics().modelCalls()).isEqualTo(1);
    assertThat(after.context().metrics().toolCalls()).isEqualTo(2);
  }

  @Test
  void ingestDoesNotIncrementToolCallsForTextOnlyResponse() {
    var conversation = AgentConversation.start(EMPTY_CONTEXT, List.of(), List.of());
    var assistant = assistantMessage("Text only");
    var tokenUsage = AgentMetrics.TokenUsage.empty();

    var after = conversation.ingest(assistant, tokenUsage);

    assertThat(after.context().metrics().toolCalls()).isEqualTo(0);
  }

  @Test
  void ingestAppendsAssistantMessageToMessages() {
    var conversation =
        AgentConversation.start(EMPTY_CONTEXT, List.of(userMessage("Hello")), List.of());
    var assistant = assistantMessage("Response");

    var after = conversation.ingest(assistant, AgentMetrics.TokenUsage.empty());

    assertThat(after.messages()).hasSize(2);
    assertThat(after.messages().getLast()).isEqualTo(assistant);
  }

  @Test
  void ingestDoesNotChangeAddedMessages() {
    var user = userMessage("Hello");
    var conversation =
        AgentConversation.start(EMPTY_CONTEXT, List.of(), List.of()).withTurn(null, List.of(user));
    var assistant = assistantMessage("Response");

    var after = conversation.ingest(assistant, AgentMetrics.TokenUsage.empty());

    assertThat(after.addedMessages()).isEqualTo(conversation.addedMessages());
  }

  @Test
  void ingestAccumulatesMetricsAcrossCalls() {
    var conversation = AgentConversation.start(EMPTY_CONTEXT, List.of(), List.of());
    var tokenUsage =
        AgentMetrics.TokenUsage.builder().inputTokenCount(10).outputTokenCount(5).build();

    var after1 = conversation.ingest(assistantMessage("R1"), tokenUsage);
    var after2 = after1.ingest(assistantMessage("R2"), tokenUsage);

    assertThat(after2.context().metrics().modelCalls()).isEqualTo(2);
    assertThat(after2.context().metrics().tokenUsage().inputTokenCount()).isEqualTo(20);
    assertThat(after2.context().metrics().tokenUsage().outputTokenCount()).isEqualTo(10);
  }

  // --- window ---

  @Test
  void windowAppliesSlidingWindowToMessages() {
    List<Message> messages = new ArrayList<>();
    for (int i = 1; i <= 5; i++) {
      messages.add(userMessage("Msg " + i));
    }
    var conversation = AgentConversation.start(EMPTY_CONTEXT, messages, List.of());

    var snapshot = conversation.window(SlidingMessagesWindow.ofSize(3));

    assertThat(snapshot.messages()).hasSize(3);
    assertThat(
            ((io.camunda.connector.agenticai.model.message.UserMessage)
                    snapshot.messages().getFirst())
                .content())
        .containsExactly(TextContent.textContent("Msg 3"));
  }

  // --- withContext ---

  @Test
  void withContextReplacesContext() {
    var conversation = AgentConversation.start(EMPTY_CONTEXT, List.of(), List.of());
    var newCtx = AgentContext.empty().withMetrics(AgentMetrics.empty().incrementModelCalls(1));

    var updated = conversation.withContext(newCtx);

    assertThat(updated.context()).isSameAs(newCtx);
    assertThat(updated.messages()).isEqualTo(conversation.messages());
    assertThat(updated.addedMessages()).isEqualTo(conversation.addedMessages());
  }

  // --- immutability ---

  @Test
  void withTurnDoesNotMutateOriginal() {
    var conversation =
        AgentConversation.start(EMPTY_CONTEXT, List.of(userMessage("Hello")), List.of());
    int originalSize = conversation.messages().size();

    conversation.withTurn(null, List.of(userMessage("World")));

    assertThat(conversation.messages()).hasSize(originalSize);
  }
}
