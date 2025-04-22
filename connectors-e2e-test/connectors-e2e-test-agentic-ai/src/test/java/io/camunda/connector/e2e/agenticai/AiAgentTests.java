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
package io.camunda.connector.e2e.agenticai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.filter;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.connector.agenticai.aiagent.memory.AgentContextChatMemoryStore;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.aiagent.provider.ChatModelFactory;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.test.SlowTest;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SlowTest
public class AiAgentTests extends BaseAgenticAiTest {

  @MockitoBean private ChatModelFactory chatModelFactory;
  @Mock private ChatLanguageModel chatModel;
  @Captor private ArgumentCaptor<ChatRequest> chatRequestCaptor;

  private JobWorker jobWorker;
  private final AtomicInteger jobWorkerCounter = new AtomicInteger(0);
  private final AtomicReference<Map<String, Object>> userFeedbackVariables =
      new AtomicReference<>(Collections.emptyMap());

  @BeforeEach
  void setUp() {
    when(chatModelFactory.createChatModel(any())).thenReturn(chatModel);

    userFeedbackVariables.set(Collections.emptyMap());
    jobWorkerCounter.set(0);

    jobWorker =
        camundaClient
            .newWorker()
            .jobType("user_feedback")
            .handler(
                (client, job) -> {
                  jobWorkerCounter.incrementAndGet();
                  client
                      .newCompleteCommand(job.getKey())
                      .variables(userFeedbackVariables.get())
                      .send()
                      .join();
                })
            .open();
  }

  @AfterEach
  void tearDown() {
    if (jobWorker != null) {
      jobWorker.close();
    }
  }

  @Nested
  class BasicFeedbackLoop {

    @Test
    void executesAgentWithoutUserFeedback() throws Exception {
      final var initialUserPrompt = "Write a haiku about the sea";
      final var expectedConversation =
          List.of(
              new SystemMessage(
                  "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
              new UserMessage(initialUserPrompt),
              new AiMessage(
                  "Endless waves whisper | moonlight dances on the tide | secrets drift below."));

      mockChatInteractions(
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(10, 20))
                          .build())
                  .aiMessage(
                      new AiMessage(
                          "Endless waves whisper | moonlight dances on the tide | secrets drift below."))
                  .build(),
              userSatisfiedFeedback()));

      final var zeebeTest =
          createProcessInstance(Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
              .waitForProcessCompletion();

      assertLastChatRequest(1, expectedConversation);

      final var agentResponse = getAgentResponse(zeebeTest);
      assertAgentResponse(
          agentResponse,
          new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20)),
          expectedConversation);

      assertThat(jobWorkerCounter.get()).isEqualTo(1);
    }

    @Test
    void executesAgentWithUserFeedback() throws Exception {
      final var initialUserPrompt = "Write a haiku about the sea";
      final var expectedConversation =
          List.of(
              new SystemMessage(
                  "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
              new UserMessage(initialUserPrompt),
              new AiMessage(
                  "Endless waves whisper | moonlight dances on the tide | secrets drift below."),
              new UserMessage("Add emojis!"),
              new AiMessage(
                  "Endless waves whisper \uD83C\uDF0A | moonlight dances on the tide \uD83C\uDF15 | secrets drift below \uD83C\uDF0C"));

      mockChatInteractions(
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(10, 20))
                          .build())
                  .aiMessage(
                      new AiMessage(
                          "Endless waves whisper | moonlight dances on the tide | secrets drift below."))
                  .build(),
              userFollowUpFeedback("Add emojis!")),
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(11, 22))
                          .build())
                  .aiMessage(
                      new AiMessage(
                          "Endless waves whisper \uD83C\uDF0A | moonlight dances on the tide \uD83C\uDF15 | secrets drift below \uD83C\uDF0C"))
                  .build(),
              userSatisfiedFeedback()));

      final var zeebeTest =
          createProcessInstance(Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
              .waitForProcessCompletion();

      assertLastChatRequest(2, expectedConversation);

      final var agentResponse = getAgentResponse(zeebeTest);
      assertAgentResponse(
          agentResponse,
          new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42)),
          expectedConversation);

      assertThat(jobWorkerCounter.get()).isEqualTo(2);
    }
  }

  @Nested
  class ToolCalling {

    @Test
    void executesAgentWithToolCallingAndUserFeedback() throws Exception {
      final var initialUserPrompt = "Explore some of your tools!";
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
              new ToolExecutionResultMessage(
                  "bbb222", "Search_The_Web", "No results for 'Where does this data come from?'"),
              new AiMessage(
                  "I played with the tools and learned that the data comes from the follow-up task and that a superflux calculation of 5 and 3 results in 24."),
              new UserMessage("So what is a superflux calculation anyway?"),
              new AiMessage(
                  "A very complex calculation only the superflux calculation tool can do."));

      mockChatInteractions(
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
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
                  .aiMessage((AiMessage) expectedConversation.get(5))
                  .build(),
              userFollowUpFeedback("So what is a superflux calculation anyway?")),
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(11, 22))
                          .build())
                  .aiMessage((AiMessage) expectedConversation.get(7))
                  .build(),
              userSatisfiedFeedback()));

      final var zeebeTest =
          createProcessInstance(Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
              .waitForProcessCompletion();

      assertLastChatRequest(3, expectedConversation);

      final var agentResponse = getAgentResponse(zeebeTest);
      assertAgentResponse(
          agentResponse,
          new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)),
          expectedConversation);

      assertThat(jobWorkerCounter.get()).isEqualTo(2);
    }
  }

  @Nested
  class Guardrails {

    @Test
    void raisesErrorWhenMaximumModelCallsAreReached() throws IOException {
      // infinite loop - always returning the same answer and handling the same user feedback
      doAnswer(
              invocationOnMock -> {
                userFeedbackVariables.set(userFollowUpFeedback("I don't like it"));
                return ChatResponse.builder()
                    .metadata(
                        ChatResponseMetadata.builder()
                            .finishReason(FinishReason.STOP)
                            .tokenUsage(new TokenUsage(10, 20))
                            .build())
                    .aiMessage(
                        new AiMessage(
                            "Endless waves whisper | moonlight dances on the tide | secrets drift below."))
                    .build();
              })
          .when(chatModel)
          .chat(chatRequestCaptor.capture());

      final var zeebeTest =
          createProcessInstance(
                  Map.of("action", "executeAgent", "userPrompt", "Write a haiku about the sea"))
              .waitForActiveIncidents();

      assertIncident(
          zeebeTest,
          incident -> {
            assertThat(incident.getElementId()).isEqualTo("AI_Agent");
            assertThat(incident.getErrorMessage())
                .isEqualTo("Maximum number of model calls reached");
          });

      final var agentResponse = getAgentResponse(zeebeTest);
      assertThat(agentResponse.context().metrics().modelCalls()).isEqualTo(10);

      final var chatMemoryMessages = chatMemoryMessages(agentResponse.context());
      assertThat(chatMemoryMessages).filteredOn(msg -> msg instanceof SystemMessage).hasSize(1);
      assertThat(chatMemoryMessages).filteredOn(msg -> msg instanceof AiMessage).hasSize(10);
      assertThat(chatMemoryMessages).filteredOn(msg -> msg instanceof UserMessage).hasSize(10);
    }
  }

  private void mockChatInteractions(ChatInteraction... chatInteractions) {
    final var queue = new ArrayList<>(Arrays.asList(chatInteractions));
    doAnswer(
            invocationOnMock -> {
              final var interaction = queue.removeFirst();
              userFeedbackVariables.set(interaction.userFeedback());
              return interaction.chatResponse();
            })
        .when(chatModel)
        .chat(chatRequestCaptor.capture());
  }

  private void assertLastChatRequest(
      int expectedChatRequestCount, List<ChatMessage> expectedConversation) {
    assertThat(chatRequestCaptor.getAllValues()).hasSize(expectedChatRequestCount);

    final var lastChatRequest = chatRequestCaptor.getValue();
    assertToolSpecifications(lastChatRequest);
    assertThat(lastChatRequest.messages())
        .as("The last chat request should contain all messages except the last response")
        .containsExactlyElementsOf(
            expectedConversation.subList(0, expectedConversation.size() - 1));
  }

  private void assertAgentResponse(
      AgentResponse agentResponse, AgentMetrics expectedMetrics, List<ChatMessage> expectedMessages)
      throws JsonProcessingException {
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.toolCalls()).isEmpty();
    assertTrue(agentResponse.context().isInState(AgentState.READY));
    assertThat(agentResponse.context().metrics()).isEqualTo(expectedMetrics);

    final var chatMemoryMessages = chatMemoryMessages(agentResponse.context());
    assertThat(chatMemoryMessages).containsExactlyElementsOf(expectedMessages);

    assertAgentResponseMessage(agentResponse, expectedMessages.getLast());
  }

  private List<ChatMessage> chatMemoryMessages(AgentContext agentContext) {
    AgentContextChatMemoryStore chatMemoryStore = new AgentContextChatMemoryStore(objectMapper);
    chatMemoryStore.loadFromAgentContext(agentContext);
    return chatMemoryStore.getMessages(AgentContextChatMemoryStore.DEFAULT_MEMORY_ID);
  }

  private void assertAgentResponseMessage(AgentResponse agentResponse, ChatMessage expectedResponse)
      throws JsonProcessingException {
    final var responseMessage =
        ChatMessageDeserializer.messageFromJson(
            objectMapper.writeValueAsString(agentResponse.chatResponse()));
    assertThat(responseMessage).isEqualTo(expectedResponse);
  }

  private void assertToolSpecifications(ChatRequest chatRequest) {
    assertThat(chatRequest.toolSpecifications())
        .hasSize(4)
        .extracting(ToolSpecification::name)
        .containsExactly("GetDateAndTime", "SuperfluxProduct", "Search_The_Web", "An_Event");
  }

  private void assertIncident(ZeebeTest zeebeTest, ThrowingConsumer<Incident> assertion) {
    final var incidents =
        camundaClient
            .newIncidentSearchRequest()
            .filter(
                filter ->
                    filter.processInstanceKey(
                        zeebeTest.getProcessInstanceEvent().getProcessInstanceKey()))
            .send()
            .join();

    assertThat(incidents.items()).hasSize(1).first().satisfies(assertion);
  }

  private AgentResponse getAgentResponse(ZeebeTest zeebeTest) throws JsonProcessingException {
    final var agentVariable =
        new CamundaDataSource(camundaClient)
                .findVariablesByProcessInstanceKey(
                    zeebeTest.getProcessInstanceEvent().getProcessInstanceKey())
                .stream()
                .filter(variable -> variable.getName().equals("agent"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Agent variable not found"));

    return objectMapper.readValue(agentVariable.getValue(), AgentResponse.class);
  }

  private Map<String, Object> userSatisfiedFeedback() {
    return Map.of("userSatisfied", true);
  }

  private Map<String, Object> userFollowUpFeedback(String followUp) {
    return Map.of("userSatisfied", false, "followUpUserPrompt", followUp);
  }

  private record ChatInteraction(ChatResponse chatResponse, Map<String, Object> userFeedback) {
    public static ChatInteraction of(ChatResponse chatResponse) {
      return new ChatInteraction(chatResponse, null);
    }

    public static ChatInteraction of(ChatResponse chatResponse, Map<String, Object> userFeedback) {
      return new ChatInteraction(chatResponse, userFeedback);
    }
  }
}
