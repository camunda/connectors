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
package io.camunda.connector.e2e.agenticai.aiagent.langchain4j.outboundconnector;

import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.langchain4j.Langchain4JAiAgentToolSpecifications.EXPECTED_A2A_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.ElementTemplate;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.common.L4JAiAgentA2aIntegrationTestSupport;
import io.camunda.connector.e2e.agenticai.assertj.AgentResponseAssert;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;

@SlowTest
@TestPropertySource(
    properties = {
      "camunda.connector.polling.enabled=true",
      "camunda.connector.webhook.enabled=true"
    })
public class L4JAiAgentConnectorA2aIntegrationTests extends BaseL4JAiAgentConnectorTest {

  @Value("classpath:agentic-ai-connectors-a2a.bpmn")
  protected Resource testProcessWithA2a;

  @Value("file:../../connectors/agentic-ai/src/main/resources/a2a/a2a-system-prompt.md")
  private Resource a2aSystemPromptResource;

  @LocalServerPort private int port;

  private L4JAiAgentA2aIntegrationTestSupport testSupport;
  private String webhookUrl;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMock) {
    testSupport = new L4JAiAgentA2aIntegrationTestSupport(a2aSystemPromptResource, objectMapper);
    testSupport.setUpWireMockStubs(wireMock, (testFile) -> testFileContent(testFile).get());
    webhookUrl = "http://localhost:%s/inbound/test-webhook-id".formatted(port);
  }

  @Override
  protected List<ToolSpecification> expectedToolSpecifications() {
    return EXPECTED_A2A_TOOL_SPECIFICATIONS;
  }

  @Override
  protected Pair<List<ChatMessage>, ZeebeTest> setupBasicTestWithoutFeedbackLoop(
      Resource process,
      Function<ElementTemplate, ElementTemplate> elementTemplateModifier,
      String responseText,
      Map<String, Object> extraProcessVariables)
      throws Exception {
    Pair<List<ChatMessage>, ZeebeTest> conversationAndTest =
        super.setupBasicTestWithoutFeedbackLoop(
            process, elementTemplateModifier, responseText, extraProcessVariables);
    List<ChatMessage> conversation =
        conversationAndTest.getLeft().stream()
            .map(
                msg -> {
                  // inject the A2A system prompt into the system message
                  if (msg instanceof SystemMessage systemMessage) {
                    return testSupport.systemMessage(systemMessage.text());
                  }
                  return msg;
                })
            .toList();
    return Pair.of(conversation, conversationAndTest.getRight());
  }

  @Test
  void executesToolDiscovery(WireMockRuntimeInfo wireMock) throws Exception {
    final var zeebeTest =
        testBasicExecutionWithoutFeedbackLoop(
            testProcessWithA2a,
            e -> e,
            HAIKU_TEXT,
            Map.of("a2aServerUrl", wireMock.getHttpBaseUrl(), "webhookUrl", webhookUrl),
            true,
            (agentResponse) ->
                AgentResponseAssert.assertThat(agentResponse)
                    .hasResponseMessageText(HAIKU_TEXT)
                    .hasResponseText(HAIKU_TEXT)
                    .hasNoResponseJson());

    testSupport.assertCompletedElementsForDiscovery(zeebeTest);
  }

  @Test
  void handlesA2aToolCalls(WireMockRuntimeInfo wireMock) throws IOException {
    final var expectedConversation = testSupport.getExpectedConversation();

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
                .aiMessage((AiMessage) expectedConversation.get(7))
                .build(),
            userFollowUpFeedback("Ok thanks, anything else?")),
        ChatInteraction.of(
            ChatResponse.builder()
                .metadata(
                    ChatResponseMetadata.builder()
                        .finishReason(FinishReason.STOP)
                        .tokenUsage(new TokenUsage(11, 22))
                        .build())
                .aiMessage((AiMessage) expectedConversation.get(9))
                .build(),
            userSatisfiedFeedback()));

    final var zeebeTest =
        createProcessInstance(
            testProcessWithA2a,
            e -> e,
            Map.of(
                "a2aServerUrl",
                wireMock.getHttpBaseUrl(),
                "webhookUrl",
                webhookUrl,
                "userPrompt",
                testSupport.initialUserPrompt));

    testSupport.callWebhookEndpointWithDelay(
        webhookUrl, testFileContent("exchange-rate-agent-webhook-payload.json").get(), 10);

    zeebeTest.waitForProcessCompletion();

    assertLastChatRequest(3, expectedConversation);

    String expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)))
                .hasResponseMessageText(expectedResponseText)
                .hasResponseText(expectedResponseText));

    assertThat(jobWorkerCounter.get()).isEqualTo(2);
  }
}
