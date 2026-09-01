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
import static org.mockito.ArgumentMatchers.eq;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Verifies the ordered sequence of {@link AgentInstanceClient} interactions a single agent
 * invocation produces, together with the actual conversation messages passed along the way.
 *
 * <p>This is a Mockito interaction verifier (hence {@code verify(...)} rather than an AssertJ
 * {@code assertThat(...)}); it asserts on calls, not on a value. Each chat turn follows the same
 * shape: {@code applyTurnStart} (moves the agent instance to {@code THINKING} and records its
 * input) followed by {@code applyTurnCompletion} (records the assistant's response and moves the
 * agent instance to its end-of-turn status). The turn snapshot passed to {@code applyTurnStart}
 * carries input messages only; the one passed to {@code applyTurnCompletion} adds the assistant
 * response and this turn's own metrics; both share the sequential 1-based iteration key.
 */
public class AgentInstanceClientVerifier {

  private final AgentInstanceClient client;
  private final InOrder inOrder;

  // Two captors on purpose: the before-chat snapshot documents that applyTurnStart fires with
  // input-only turns, the after-chat snapshot that applyTurnCompletion fires with the assistant
  // response attached.
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

  /** A chat turn that ends in a tool call: ... → {@code TOOL_CALLING} + this turn's metrics. */
  public AgentInstanceClientVerifier toolCallTurn(
      AgentMetrics metrics, Consumer<ChatTurnAssert> turnAssertions) {
    return chatTurn(AgentInstanceUpdateStatus.TOOL_CALLING, metrics, turnAssertions);
  }

  /** A chat turn that ends with the final answer: ... → {@code IDLE} + this turn's metrics. */
  public AgentInstanceClientVerifier finalAnswerTurn(
      AgentMetrics metrics, Consumer<ChatTurnAssert> turnAssertions) {
    return chatTurn(AgentInstanceUpdateStatus.IDLE, metrics, turnAssertions);
  }

  private AgentInstanceClientVerifier chatTurn(
      AgentInstanceUpdateStatus endStatus,
      AgentMetrics metrics,
      Consumer<ChatTurnAssert> turnAssertions) {
    inOrder
        .verify(client)
        .applyTurnStart(any(), any(), any(), beforeChatTurns.capture(), any(), any());
    inOrder
        .verify(client)
        .applyTurnCompletion(any(), any(), afterChatTurns.capture(), any(), eq(endStatus));

    final var expectedIterationKey = ++turnCount;
    final var before = lastValue(beforeChatTurns);
    final var after = lastValue(afterChatTurns);

    // before-chat snapshot: input only, no assistant response yet
    assertThat(before.iterationKey()).isEqualTo(expectedIterationKey);
    assertThat(before.assistantMessage()).isNull();
    // after-chat snapshot: same turn, now with the assistant response and per-turn execution time
    assertThat(after.iterationKey()).isEqualTo(expectedIterationKey);
    assertThat(after.metrics().executionTime()).isNotNull();
    // this turn's own metrics (modelCalls/tokens/toolCalls), ignoring the execution-time reading
    assertThat(after.metrics().withExecutionTime(null)).isEqualTo(metrics);

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
      assertThat(toolCallResultMessage().results())
          .isNotEmpty()
          .allSatisfy(r -> assertThat(r.elementId()).isNotNull().isEqualTo(r.name()));
      return this;
    }

    /** The {@code completedAt} of the tool call result with the given id. */
    public OffsetDateTime toolResultCompletedAt(String toolCallId) {
      return toolCallResultMessage().results().stream()
          .filter(r -> toolCallId.equals(r.id()))
          .findFirst()
          .orElseThrow(() -> new AssertionError("no tool call result with id '" + toolCallId + "'"))
          .completedAt();
    }

    private ToolCallResultMessage toolCallResultMessage() {
      return before.inputMessages().stream()
          .filter(ToolCallResultMessage.class::isInstance)
          .map(ToolCallResultMessage.class::cast)
          .findFirst()
          .orElseThrow(() -> new AssertionError("no tool call result message in input"));
    }

    /** The assistant responded with a single tool call to the named tool. */
    public ChatTurnAssert callingTool(String expectedToolName) {
      assertThat(after.assistantMessage().toolCalls())
          .singleElement()
          .extracting(ToolCall::name)
          .isEqualTo(expectedToolName);
      return this;
    }

    /** The assistant responded with tool calls to exactly the named tools, in any order. */
    public ChatTurnAssert callingTools(String... expectedToolNames) {
      assertThat(after.assistantMessage().toolCalls())
          .extracting(ToolCall::name)
          .containsExactlyInAnyOrder(expectedToolNames);
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
