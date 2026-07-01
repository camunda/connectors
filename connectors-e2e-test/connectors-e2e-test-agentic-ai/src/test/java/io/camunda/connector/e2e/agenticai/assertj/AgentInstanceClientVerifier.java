/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai.assertj;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.api.command.AgentInstanceUpdateStatus;
import io.camunda.connector.agenticai.aiagent.agentinstance.AgentInstanceClient;
import io.camunda.connector.agenticai.aiagent.model.AgentConversationTurn;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Verifies the ordered sequence of {@link AgentInstanceClient} interactions a single agent
 * invocation produces, together with the actual conversation messages passed to the history-item
 * calls.
 *
 * <p>This is a Mockito interaction verifier (hence {@code verify(...)} rather than an AssertJ
 * {@code assertThat(...)}); it asserts on calls, not on a value. Each chat turn follows the same
 * shape: {@code THINKING} (status only) → {@code createHistoryItemsBeforeChat} → {@code
 * createHistoryItemsAfterChat} → a final {@code update} carrying the end status and the per-turn
 * metrics delta. The before-chat snapshot carries input messages only; the after-chat snapshot adds
 * the assistant response; both share the sequential 1-based iteration key.
 */
public class AgentInstanceClientVerifier {

  private final AgentInstanceClient client;
  private final InOrder inOrder;

  // Two captors on purpose: the before-chat snapshot documents that createHistoryItemsBeforeChat
  // fires with input-only turns, the after-chat snapshot that createHistoryItemsAfterChat fires
  // with the assistant response attached.
  private final ArgumentCaptor<AgentConversationTurn> beforeChatTurns =
      ArgumentCaptor.forClass(AgentConversationTurn.class);
  private final ArgumentCaptor<AgentConversationTurn> afterChatTurns =
      ArgumentCaptor.forClass(AgentConversationTurn.class);

  private int turnCount = 0;

  private AgentInstanceClientVerifier(AgentInstanceClient client) {
    this.client = client;
    this.inOrder = inOrder(client);
  }

  public static AgentInstanceClientVerifier verify(AgentInstanceClient client) {
    return new AgentInstanceClientVerifier(client);
  }

  public AgentInstanceClientVerifier createdInstance() {
    inOrder.verify(client).create(any());
    return this;
  }

  /** A chat turn that ends in a tool call: ... → {@code TOOL_CALLING} + metrics delta. */
  public AgentInstanceClientVerifier toolCallTurn(
      AgentMetrics delta, Consumer<ChatTurnAssert> turnAssertions) {
    return chatTurn(AgentInstanceUpdateStatus.TOOL_CALLING, delta, turnAssertions);
  }

  /** A chat turn that ends with the final answer: ... → {@code IDLE} + metrics delta. */
  public AgentInstanceClientVerifier finalAnswerTurn(
      AgentMetrics delta, Consumer<ChatTurnAssert> turnAssertions) {
    return chatTurn(AgentInstanceUpdateStatus.IDLE, delta, turnAssertions);
  }

  private AgentInstanceClientVerifier chatTurn(
      AgentInstanceUpdateStatus endStatus,
      AgentMetrics delta,
      Consumer<ChatTurnAssert> turnAssertions) {
    inOrder
        .verify(client)
        .update(
            any(),
            any(),
            argThat(
                request ->
                    request != null
                        && request.status() == AgentInstanceUpdateStatus.THINKING
                        && request.delta() == null
                        && request.tools() != null
                        && !request.tools().isEmpty()));
    inOrder
        .verify(client)
        .createHistoryForInputMessages(any(), any(), beforeChatTurns.capture(), any());
    inOrder.verify(client).createHistoryForAssistantMessage(any(), any(), afterChatTurns.capture());
    inOrder
        .verify(client)
        .update(
            any(),
            any(),
            argThat(
                request ->
                    request != null
                        && request.status() == endStatus
                        && Objects.equals(request.delta(), delta)
                        && (request.tools() == null || request.tools().isEmpty())));

    final var expectedIterationKey = ++turnCount;
    final var before = lastValue(beforeChatTurns);
    final var after = lastValue(afterChatTurns);

    // before-chat snapshot: input only, no assistant response yet
    assertThat(before.iterationKey()).isEqualTo(expectedIterationKey);
    assertThat(before.assistantMessage()).isNull();
    // after-chat snapshot: same turn, now with the assistant response and per-turn execution time
    assertThat(after.iterationKey()).isEqualTo(expectedIterationKey);
    assertThat(after.metrics().executionTime()).isNotNull();

    turnAssertions.accept(new ChatTurnAssert(before, after));
    return this;
  }

  public AgentInstanceClientVerifier noMoreInteractions() {
    verifyNoMoreInteractions(client);
    return this;
  }

  private static AgentConversationTurn lastValue(ArgumentCaptor<AgentConversationTurn> captor) {
    final var values = captor.getAllValues();
    return values.get(values.size() - 1);
  }

  /**
   * Asserts the actual messages of a single chat turn: the input messages on the before-chat
   * snapshot, the assistant response on the after-chat snapshot.
   */
  public static final class ChatTurnAssert {
    private final AgentConversationTurn before;
    private final AgentConversationTurn after;

    private ChatTurnAssert(AgentConversationTurn before, AgentConversationTurn after) {
      this.before = before;
      this.after = after;
    }

    /** A single user-prompt input message with the given text. */
    public ChatTurnAssert fromUserPrompt(String expectedText) {
      assertThat(before.inputMessages())
          .singleElement()
          .isInstanceOfSatisfying(
              UserMessage.class,
              message -> assertThat(textContent(message.content())).isEqualTo(expectedText));
      return this;
    }

    /**
     * Input messages contain tool call results (a follow-up turn after a tool call round). Also
     * asserts every result carries its resolved BPMN element id (== tool name for ad-hoc tools).
     */
    public ChatTurnAssert fromToolResults() {
      final var toolResultMessage =
          before.inputMessages().stream()
              .filter(ToolCallResultMessage.class::isInstance)
              .map(ToolCallResultMessage.class::cast)
              .findFirst()
              .orElseThrow(() -> new AssertionError("no tool call result message in input"));
      assertThat(toolResultMessage.results())
          .isNotEmpty()
          .allSatisfy(r -> assertThat(r.elementId()).isNotNull().isEqualTo(r.name()));
      return this;
    }

    /** The assistant responded with a single tool call to the named tool. */
    public ChatTurnAssert callingTool(String expectedToolName) {
      assertThat(after.assistantMessage().toolCalls())
          .singleElement()
          .extracting(ToolCall::name)
          .isEqualTo(expectedToolName);
      return this;
    }

    /** The assistant responded with the given final answer text and no tool calls. */
    public ChatTurnAssert answering(String expectedText) {
      assertThat(after.assistantMessage().toolCalls()).isEmpty();
      assertThat(textContent(after.assistantMessage().content())).isEqualTo(expectedText);
      return this;
    }

    private static String textContent(List<Content> content) {
      return content.stream()
          .filter(TextContent.class::isInstance)
          .map(c -> ((TextContent) c).text())
          .collect(Collectors.joining());
    }
  }
}
