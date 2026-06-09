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

import static io.camunda.connector.e2e.agenticai.TestUtil.postWithDelay;
import static io.camunda.connector.e2e.agenticai.aiagent.AiAgentTestFixtures.HAIKU_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.langchain4j.Langchain4JAiAgentToolSpecifications.EXPECTED_A2A_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.e2e.agenticai.TestUtil;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.common.L4JAiAgentA2aIntegrationTestSupport;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.OpenAiChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.langchain4j.wiremock.RecordedLlmConversation;
import io.camunda.connector.e2e.agenticai.assertj.JobWorkerAgentResponseAssert;
import io.camunda.connector.e2e.inbound.InboundConnectorTestConfiguration;
import io.camunda.connector.e2e.inbound.InboundConnectorTestConfiguration.InboundConnectorTestHelper;
import io.camunda.connector.runtime.inbound.importer.ImportSchedulers;
import io.camunda.connector.test.utils.annotation.SlowTest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.test.context.TestPropertySource;

@SlowTest
@TestPropertySource(
    properties = {
      "camunda.connector.polling.enabled=true",
      "camunda.connector.webhook.enabled=true"
    })
@Import(InboundConnectorTestConfiguration.class)
public class L4JAiAgentJobWorkerA2aIntegrationTests extends BaseWireMockL4JAiAgentJobWorkerTest {

  public static final String WEBHOOK_ELEMENT_ID = "Wait_For_Completion_Webhook";

  @Autowired private InboundConnectorTestHelper inboundConnectorTestHelper;
  @Autowired private ImportSchedulers importSchedulers;

  @Value("classpath:agentic-ai-ahsp-connectors-a2a.bpmn")
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

    // clear process definition caches & reset executables from previous tests
    inboundConnectorTestHelper.setUpTest();
  }

  @Override
  protected String expectedSystemPrompt() {
    return testSupport.systemMessage(SYSTEM_PROMPT).text();
  }

  @Override
  protected List<ToolSpecification> expectedToolSpecifications() {
    return EXPECTED_A2A_TOOL_SPECIFICATIONS;
  }

  @Test
  void executesToolDiscovery() throws Exception {
    final var zeebeTest =
        testBasicExecutionWithoutFeedbackLoop(
            testProcessWithA2a,
            e -> e,
            Map.of("a2aServerUrl", wireMock.getHttpBaseUrl(), "webhookUrl", webhookUrl),
            HAIKU_TEXT,
            true,
            (agentResponse) ->
                JobWorkerAgentResponseAssert.assertThat(agentResponse)
                    .hasResponseMessageText(HAIKU_TEXT)
                    .hasResponseText(HAIKU_TEXT)
                    .hasNoResponseJson());

    testSupport.assertCompletedElementsForDiscovery(zeebeTest);
  }

  @Test
  void handlesA2aToolCalls() throws IOException {
    final var expectedConversation = testSupport.getExpectedConversation();
    final var firstAiMessage = (AiMessage) expectedConversation.get(2);
    final var secondAiMessage = (AiMessage) expectedConversation.get(7);
    final var finalAiMessage = (AiMessage) expectedConversation.get(9);

    OpenAiChatModelStubs.stubConversation(
        Turn.toolCalls(
            firstAiMessage.text(),
            10,
            20,
            ToolCall.of("aaa111", "SuperfluxProduct", "{\"a\":10,\"b\":3}"),
            ToolCall.of("bbb222", "A2A_Travel_Agent", "{\"text\":\"Book a single hotel room\"}"),
            ToolCall.of(
                "ccc333", "A2A_Weather_Agent", "{\"text\":\"What's the weather in London?\"}"),
            ToolCall.of(
                "ddd444",
                "A2A_Exchange_Rate_Agent",
                "{\"text\":\"What's the exchange rate for USD to EUR?\"}")),
        Turn.text(secondAiMessage.text(), 100, 200),
        Turn.text(finalAiMessage.text(), 11, 22));

    enqueueUserFeedback(userFollowUpFeedback("Ok thanks, anything else?"), userSatisfiedFeedback());

    final var travelResult = ((ToolExecutionResultMessage) expectedConversation.get(4)).text();
    final var weatherResult = ((ToolExecutionResultMessage) expectedConversation.get(5)).text();
    final var exchangeRateResult =
        ((ToolExecutionResultMessage) expectedConversation.get(6)).text();

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

    TestUtil.awaitInboundConnectorReady(
        zeebeTest, WEBHOOK_ELEMENT_ID, importSchedulers, inboundConnectorTestHelper);
    postWithDelay(
        webhookUrl, testFileContent("exchange-rate-agent-webhook-payload.json").get(), 100);

    zeebeTest.waitForProcessCompletion();

    final var recorded = RecordedLlmConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    assertConversationMessages(
        recorded.lastRequest(),
        ExpectedMessage.system(expectedSystemPrompt()),
        ExpectedMessage.user(testSupport.initialUserPrompt),
        ExpectedMessage.assistantWithToolCalls(
            firstAiMessage.text(),
            "SuperfluxProduct",
            "A2A_Travel_Agent",
            "A2A_Weather_Agent",
            "A2A_Exchange_Rate_Agent"),
        ExpectedMessage.toolResult("aaa111", "39"),
        ExpectedMessage.toolResult("bbb222", travelResult),
        ExpectedMessage.toolResult("ccc333", weatherResult),
        ExpectedMessage.toolResult("ddd444", exchangeRateResult),
        ExpectedMessage.assistant(secondAiMessage.text()),
        ExpectedMessage.user("Ok thanks, anything else?"));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            JobWorkerAgentResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242), 4))
                .hasResponseMessageText(finalAiMessage.text())
                .hasResponseText(finalAiMessage.text()));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }
}
