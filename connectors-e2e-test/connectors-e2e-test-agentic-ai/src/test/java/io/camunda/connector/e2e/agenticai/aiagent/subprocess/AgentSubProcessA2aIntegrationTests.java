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
package io.camunda.connector.e2e.agenticai.aiagent.subprocess;

import static io.camunda.connector.e2e.agenticai.TestUtil.postWithDelay;
import static io.camunda.connector.e2e.agenticai.aiagent.AgentTestFixtures.HAIKU_TEXT;
import static io.camunda.connector.e2e.agenticai.aiagent.AgentToolSpecifications.EXPECTED_A2A_TOOL_SPECIFICATIONS;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.e2e.agenticai.TestUtil;
import io.camunda.connector.e2e.agenticai.aiagent.common.AgentA2aIntegrationTestSupport;
import io.camunda.connector.e2e.agenticai.aiagent.common.AgentA2aIntegrationTestSupport.A2aExpectedConversation;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.ToolCall;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsChatModelStubs.Turn;
import io.camunda.connector.e2e.agenticai.aiagent.wiremock.openai.OpenAiCompletionsRecordedConversation;
import io.camunda.connector.e2e.agenticai.assertj.AgentSubProcessResponseAssert;
import io.camunda.connector.e2e.inbound.InboundConnectorTestConfiguration;
import io.camunda.connector.e2e.inbound.InboundConnectorTestConfiguration.InboundConnectorTestHelper;
import io.camunda.connector.runtime.inbound.importer.ImportSchedulers;
import io.camunda.connector.test.utils.annotation.SlowTest;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
      "camunda.connector.webhook.enabled=true",
      "camunda.connector.polling.interval=2000"
    })
@Import(InboundConnectorTestConfiguration.class)
public class AgentSubProcessA2aIntegrationTests extends BaseAgentSubProcessTest {

  public static final String WEBHOOK_ELEMENT_ID = "Wait_For_Completion_Webhook";

  @Autowired private InboundConnectorTestHelper inboundConnectorTestHelper;
  @Autowired private ImportSchedulers importSchedulers;

  @Value("classpath:agentic-ai-ahsp-connectors-a2a.bpmn")
  protected Resource testProcessWithA2a;

  @Value(
      "file:../../connectors/agentic-ai/connector-agentic-ai/src/main/resources/a2a/a2a-system-prompt.md")
  private Resource a2aSystemPromptResource;

  @LocalServerPort private int port;

  private AgentA2aIntegrationTestSupport testSupport;
  private String webhookUrl;
  private String webhookContext;

  @BeforeEach
  void setUp() {
    testSupport = new AgentA2aIntegrationTestSupport(a2aSystemPromptResource, objectMapper);
    testSupport.setUpWireMockStubs(wireMock, (testFile) -> testFileContent(testFile).get());
    // Unique webhook context per test so consecutive A2A tests/reruns don't collide on the same
    // inbound context in the shared per-class runtime. webhookUrl must match the registered path.
    webhookContext = "test-webhook-id-" + UUID.randomUUID();
    webhookUrl = "http://localhost:%s/inbound/%s".formatted(port, webhookContext);

    // clear process definition caches & reset executables from previous tests
    inboundConnectorTestHelper.setUpTest();
  }

  @Override
  protected BpmnModelInstance customizeModel(BpmnModelInstance model) {
    TestUtil.setWebhookContext(model, WEBHOOK_ELEMENT_ID, webhookContext);
    return model;
  }

  @Override
  protected String expectedSystemPrompt() {
    return testSupport.augmentedSystemPrompt(SYSTEM_PROMPT);
  }

  @Override
  protected List<ToolDefinition> expectedTools() {
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
                AgentSubProcessResponseAssert.assertThat(agentResponse)
                    .hasResponseMessageText(HAIKU_TEXT)
                    .hasResponseText(HAIKU_TEXT)
                    .hasNoResponseJson());

    testSupport.assertCompletedElementsForDiscovery(zeebeTest);
  }

  @Test
  void handlesA2aToolCalls() throws IOException {
    final A2aExpectedConversation conversation = testSupport.getExpectedConversation();

    OpenAiCompletionsChatModelStubs.stubConversation(
        Turn.toolCalls(
            conversation.aiToolCallMessageText(),
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
        Turn.text(conversation.secondAiMessageText(), 100, 200),
        Turn.text(conversation.finalAiMessageText(), 11, 22));

    enqueueUserFeedback(userFollowUpFeedback("Ok thanks, anything else?"), userSatisfiedFeedback());

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

    awaitProcessCompletion(zeebeTest);

    final var recorded = OpenAiCompletionsRecordedConversation.recorded();
    assertThat(recorded.modelCallCount()).isEqualTo(3);

    assertConversationMessages(
        recorded.lastRequest(),
        ExpectedMessage.system(expectedSystemPrompt()),
        ExpectedMessage.user(testSupport.initialUserPrompt),
        ExpectedMessage.assistantWithToolCalls(
            conversation.aiToolCallMessageText(),
            "SuperfluxProduct",
            "A2A_Travel_Agent",
            "A2A_Weather_Agent",
            "A2A_Exchange_Rate_Agent"),
        ExpectedMessage.toolCallResult("aaa111", conversation.superfluxToolResult()),
        ExpectedMessage.toolCallResult("bbb222", conversation.travelAgentToolResult()),
        ExpectedMessage.toolCallResult("ccc333", conversation.weatherAgentToolResult()),
        ExpectedMessage.toolCallResult("ddd444", conversation.exchangeRateAgentToolResult()),
        ExpectedMessage.assistant(conversation.secondAiMessageText()),
        ExpectedMessage.user("Ok thanks, anything else?"));

    assertAgentResponse(
        zeebeTest,
        agentResponse ->
            AgentSubProcessResponseAssert.assertThat(agentResponse)
                .isReady()
                .hasMetrics(new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242), 4))
                .hasResponseMessageText(conversation.finalAiMessageText())
                .hasResponseText(conversation.finalAiMessageText()));

    assertThat(userFeedbackJobWorkerCounter.get()).isEqualTo(2);
  }
}
