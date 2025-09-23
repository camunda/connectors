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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.jobworker;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

@SlowTest
public class L4JAiAgentJobWorkerEventsTests extends BaseL4JAiAgentJobWorkerTest {

  @Value("classpath:agentic-ai-ahsp-connectors-event.bpmn")
  private Resource testProcessWithEvent;

  private static final long MESSAGE_PUBLICATION_DELAY = 2000;

  @Test
  void messagePublishedBeforeProcessActivation() throws Exception {
    final var initialUserPrompt = "Explore some of your tools!";

    final var expectedConversation =
        List.of(
            new SystemMessage(
                "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
            new UserMessage(initialUserPrompt),
            new UserMessage("Updated message from event: do not use any tools anymore."),
            new AiMessage(
                "Alright, I will not use any tools anymore. How can I assist you further?"));

    mockChatInteractions(
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(100, 200))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(3))
                .build(),
            userSatisfiedFeedback()));

    publishMessage();

    final var zeebeTest =
        createProcessInstance(
            testProcessWithEvent,
            e -> e,
            Map.of("action", "executeAgent", "userPrompt", initialUserPrompt));

    zeebeTest.waitForProcessCompletion();

    assertLastChatRequest(expectedConversation, false);

    String expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(100, 200)))
                .hasResponseMessageText(expectedResponseText)
                .hasResponseText(expectedResponseText));

    assertThat(jobWorkerCounter.get()).isEqualTo(1);
  }

  @Test
  void nonInterruptingMessagePublishedAfterProcessActivation() throws Exception {
    executesAgentWithToolCallingAndEvent(false);
  }

  @Test
  void interruptingMessagePublishedAfterProcessActivation() throws Exception {
    executesAgentWithToolCallingAndEvent(true);
  }

  private void executesAgentWithToolCallingAndEvent(boolean interruptToolCalls) throws Exception {
    final var initialUserPrompt = "Explore some of your tools!";
    final var secondToolResult =
        interruptToolCalls
            ? "Tool execution was canceled."
            : "No results for 'Where does this data come from?'";

    final var expectedConversation =
        List.of(
            new SystemMessage(
                "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
            new UserMessage(initialUserPrompt),
            new AiMessage(
                "The user asked me to call some of my tools. I will call the superflux calculation and the task with a text input schema as they look interesting to me.",
                List.of(
                    ToolExecutionRequest.builder()
                        .id("aaa111")
                        .name("SuperfluxProduct")
                        .arguments("{\"a\": 5, \"b\": 3}")
                        .build(),
                    ToolExecutionRequest.builder()
                        .id("bbb222")
                        .name("Search_The_Web")
                        .arguments("{\"searchQuery\": \"Where does this data come from?\"}")
                        .build())),
            new ToolExecutionResultMessage("aaa111", "SuperfluxProduct", "24"),
            new ToolExecutionResultMessage("bbb222", "Search_The_Web", secondToolResult),
            new UserMessage("Updated message from event: do not use any tools anymore."),
            new AiMessage(
                "Alright, I will not use any tools anymore. How can I assist you further?"));

    mockChatInteractions(
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.TOOL_EXECUTION)
                        .tokenUsage(new TokenUsage(10, 20))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(2))
                .build()),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(100, 200))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(6))
                .build(),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(
            testProcessWithEvent,
            e ->
                interruptToolCalls ? e.property("data.events.behavior", "INTERRUPT_TOOL_CALLS") : e,
            Map.of("action", "executeAgent", "userPrompt", initialUserPrompt));

    publishMessageWithDelay();

    zeebeTest.waitForProcessCompletion();

    assertLastChatRequest(expectedConversation, false);

    String expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(2, new AgentMetrics.TokenUsage(110, 220)))
                .hasResponseMessageText(expectedResponseText)
                .hasResponseText(expectedResponseText));

    assertThat(jobWorkerCounter.get()).isEqualTo(1);
  }

  private void publishMessage() {
    var messageKey =
        camundaClient
            .newPublishMessageCommand()
            .messageName("ai-agent-message")
            .correlationKey("ai-agent-message-correlation")
            .variable("messageFromEvent", "do not use any tools anymore.")
            .timeToLive(Duration.of(5, ChronoUnit.SECONDS))
            .execute()
            .getMessageKey();
    System.out.println("Published message with key: " + messageKey);
  }

  private void publishMessageWithDelay() {
    CompletableFuture.delayedExecutor(MESSAGE_PUBLICATION_DELAY, TimeUnit.MILLISECONDS)
        .execute(this::publishMessage);
  }
}
