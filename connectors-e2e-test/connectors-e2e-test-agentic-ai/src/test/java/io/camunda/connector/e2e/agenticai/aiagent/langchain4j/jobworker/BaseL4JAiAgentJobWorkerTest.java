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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.adhoctoolsschema.schema.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentResponseModel;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.BaseAiAgentJobWorkerTest;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.e2e.agenticai.assertj.ToolExecutionRequestEqualsPredicate;
import io.camunda.connector.test.SlowTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.ThrowingConsumer;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.core.io.Resource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SlowTest
abstract class BaseL4JAiAgentJobWorkerTest extends BaseAiAgentJobWorkerTest {
  @MockitoBean private ChatModelFactory chatModelFactory;
  @Mock protected ChatModel chatModel;
  @Captor protected ArgumentCaptor<ChatRequest> chatRequestCaptor;
  @MockitoSpyBean protected AdHocToolsSchemaResolver toolsSchemaResolver;

  @BeforeEach
  void setUp() {
    when(chatModelFactory.createChatModel(any())).thenReturn(chatModel);
  }

  protected ZeebeTest testBasicExecutionWithoutFeedbackLoop(
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      String responseText,
      boolean assertToolSpecifications,
      ThrowingConsumer<AgentResponse> agentResponseAssertions)
      throws Exception {
    return testBasicExecutionWithoutFeedbackLoop(
        testProcess,
        elementTemplateModifier,
        responseText,
        assertToolSpecifications,
        agentResponseAssertions);
  }

  protected ZeebeTest testBasicExecutionWithoutFeedbackLoop(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      String responseText,
      boolean assertToolSpecifications,
      ThrowingConsumer<AgentResponse> agentResponseAssertions)
      throws Exception {
    final var testSetup =
        setupBasicTestWithoutFeedbackLoop(process, elementTemplateModifier, responseText);

    final var zeebeTest = testSetup.getRight();
    zeebeTest.waitForProcessCompletion();

    assertLastChatRequest(1, testSetup.getLeft(), assertToolSpecifications);

    assertAgentResponse(
        testSetup.getRight(),
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20)))
                .satisfies(agentResponseAssertions));

    assertThat(jobWorkerCounter.get()).isEqualTo(1);

    return zeebeTest;
  }

  protected Pair<List<ChatMessage>, ZeebeTest> setupBasicTestWithoutFeedbackLoop(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      String responseText)
      throws Exception {
    final var initialUserPrompt = "Write a haiku about the sea";
    final var expectedConversation =
        List.of(
            new SystemMessage(
                "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
            new UserMessage(initialUserPrompt),
            new AiMessage(responseText));

    mockChatInteractions(
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(10, 20))
                        .build())
                .aiMessage(new AiMessage(responseText))
                .build(),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(
            process,
            elementTemplateModifier,
            Map.of("action", "executeAgent", "userPrompt", initialUserPrompt));

    return Pair.of(expectedConversation, zeebeTest);
  }

  protected ZeebeTest testInteractionWithToolsAndUserFeedbackLoops(
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      String responseText,
      boolean assertToolSpecifications,
      ThrowingConsumer<AgentResponse> agentResponseAssertions)
      throws Exception {
    return testInteractionWithToolsAndUserFeedbackLoops(
        testProcess,
        elementTemplateModifier,
        responseText,
        assertToolSpecifications,
        agentResponseAssertions);
  }

  protected ZeebeTest testInteractionWithToolsAndUserFeedbackLoops(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      String responseText,
      boolean assertToolSpecifications,
      ThrowingConsumer<AgentResponse> agentResponseAssertions)
      throws Exception {
    final var testSetup =
        setupInteractionWithToolsAndUserFeedbackLoops(
            process, elementTemplateModifier, responseText);

    final var zeebeTest = testSetup.getRight();
    zeebeTest.waitForProcessCompletion();

    assertLastChatRequest(3, testSetup.getLeft(), assertToolSpecifications);

    assertAgentResponse(
        testSetup.getRight(),
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasNoToolCalls()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)))
                .satisfies(agentResponseAssertions));

    assertThat(jobWorkerCounter.get()).isEqualTo(2);

    return zeebeTest;
  }

  protected Pair<List<ChatMessage>, ZeebeTest> setupInteractionWithToolsAndUserFeedbackLoops(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      String responseText)
      throws Exception {
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
            new AiMessage(responseText));

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
                .aiMessage(new AiMessage(responseText))
                .build(),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(
            process,
            elementTemplateModifier,
            Map.of("action", "executeAgent", "userPrompt", initialUserPrompt));

    return Pair.of(expectedConversation, zeebeTest);
  }

  protected void mockChatInteractions(ChatInteraction... chatInteractions) {
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

  protected void assertLastChatRequest(
      int expectedChatRequestCount, List<ChatMessage> expectedConversation) {
    assertLastChatRequest(expectedChatRequestCount, expectedConversation, true);
  }

  protected void assertLastChatRequest(
      int expectedChatRequestCount,
      List<ChatMessage> expectedConversation,
      boolean assertToolSpecifications) {
    assertThat(chatRequestCaptor.getAllValues()).hasSize(expectedChatRequestCount);

    final var lastChatRequest = chatRequestCaptor.getValue();

    if (assertToolSpecifications) {
      assertToolSpecifications(lastChatRequest);
    }

    assertThat(lastChatRequest.messages())
        .as("The last chat request should contain all messages except the last response")
        .usingRecursiveFieldByFieldElementComparator(
            RecursiveComparisonConfiguration.builder()
                .withEqualsForType(
                    new ToolExecutionRequestEqualsPredicate(), ToolExecutionRequest.class)
                .build())
        .containsExactlyElementsOf(
            expectedConversation.subList(0, expectedConversation.size() - 1));
  }

  protected void assertToolSpecifications(ChatRequest chatRequest) {
    assertThat(chatRequest.toolSpecifications())
        .extracting(ToolSpecification::name)
        .containsExactly(
            "GetDateAndTime",
            "SuperfluxProduct",
            "Search_The_Web",
            "A_Complex_Tool",
            "Download_A_File",
            "An_Event");
  }

  protected record ChatInteraction(ChatResponse chatResponse, Map<String, Object> userFeedback) {
    protected static ChatInteraction of(ChatResponse chatResponse) {
      return new ChatInteraction(chatResponse, null);
    }

    protected static ChatInteraction of(
        ChatResponse chatResponse, Map<String, Object> userFeedback) {
      return new ChatInteraction(chatResponse, userFeedback);
    }
  }

  protected record DownloadFileToolResult(int status, DocumentToContentResponseModel document) {}
}
